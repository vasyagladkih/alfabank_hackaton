#!/usr/bin/env python3
"""Real HTTP acceptance through Nginx. Use a disposable, freshly seeded demo database.

No route interception, DB shortcuts, external providers, credentials in output, or reset.
This suite makes synthetic decisions and is intentionally separate from browser fixtures.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Stack:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.tokens = {}
        self.passed = 0

    def call(self, path, role=None, body=None, expected=200, headers=None, raw=None):
        request_headers = {'Accept': 'application/json', **(headers or {})}
        if role:
            request_headers['Authorization'] = 'Bearer ' + self.tokens[role]
        if body is not None or raw is not None:
            request_headers['Content-Type'] = 'application/json'
        payload = raw if raw is not None else json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=payload, headers=request_headers)
        try:
            response = urllib.request.urlopen(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.status
            response_headers = dict(response.headers)
            content = response.read()
        try:
            value = json.loads(content) if content else None
        except json.JSONDecodeError:
            value = content.decode(errors='replace')
        if expected is not None:
            assert status == expected, f'{req.get_method()} {path}: expected {expected}, got {status}: {value}'
            if expected >= 400:
                assert isinstance(value, dict) and all(isinstance(value.get(key), str)
                    for key in ('code', 'message', 'timestamp')), f'{path}: missing structured error contract'
                assert datetime.fromisoformat(value['timestamp'].replace('Z', '+00:00')).tzinfo is not None
        return status, value, response_headers

    def check(self, name, condition=True):
        assert condition, name
        self.passed += 1
        print('PASS', name, flush=True)

    def login(self):
        for role in ('client_good', 'client_bad', 'bank_employee'):
            _, result, _ = self.call('/api/v1/auth/demo-login', body={'userCode': role})
            self.tokens[role] = result['token']
            expected_role = 'EMPLOYEE' if role == 'bank_employee' else 'CLIENT'
            self.check(f'demo login {role}', result['user']['role'] == expected_role)

    def get(self, path, role):
        return self.call(path, role)[1]

    def create(self, role='client_good', amount=1_500_000):
        return self.call('/api/v1/client/applications', role,
                         {'amount': amount, 'termMonths': 12, 'purpose': 'EQUIPMENT'}, 201)[1]

    def wait(self, app, role='client_good', timeout=45):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            result = self.get(f'/api/v1/client/applications/{app["id"]}', role)
            if result['status'] != 'PROCESSING':
                return result
            time.sleep(0.25)
        raise AssertionError(f'application {app["id"]} stayed PROCESSING')

    def dossier(self, app_id):
        return self.get(f'/api/v1/employee/applications/{app_id}', 'bank_employee')

    def command(self, dossier, kind, amount=None):
        command = {'expectedVersion': dossier['application']['version'], 'requestId': str(uuid.uuid4()),
                   'type': kind, 'rationale': 'Учебное рассмотрение синтетических данных, без финансовых последствий.'}
        if amount is not None:
            command['offerAmount'] = amount
        return command

    def decide(self, dossier, command, expected=200):
        return self.call(f'/api/v1/employee/applications/{dossier["application"]["id"]}/decisions',
                         'bank_employee', command, expected)[1]

    def manual(self, action, excluded=()):
        for app in self.get('/api/v1/employee/applications?status=MANUAL_REVIEW', 'bank_employee'):
            if app['id'] in excluded:
                continue
            dossier = self.dossier(app['id'])
            if action in dossier['allowedActions']:
                return dossier
        raise AssertionError(f'No unconsumed {action} fixture: run against a fresh disposable demo database')


def verify_recovered(stack, state_file):
    app = json.loads(state_file.read_text())
    result = stack.wait(app)
    stack.check('pending application survived process restart', result['status'] == 'APPROVED')
    dossier = stack.dossier(app['id'])
    completions = [event for event in dossier['audit']
                   if event['actorRole'] == 'SYSTEM' and event.get('fromStatus') == 'PROCESSING']
    stack.check('exactly one scoring completion after restart', len(completions) == 1)
    stack.check('snapshot hash unchanged after restart',
                dossier.get('snapshotSha256') == app['snapshotSha256'])


def smoke(stack):
    _, health, _ = stack.call('/api/health')
    stack.check('Nginx proxies live database readiness', health == {'status': 'UP'})
    _, page, _ = stack.call('/client')
    stack.check('SPA deep-link served through Nginx', '<!doctype html>' in page.lower())
    stack.call('/api/v1/client/applications', expected=401)
    stack.call('/api/v1/client/applications', expected=401, headers={'Authorization': 'Bearer placeholder-demo-token'})
    stack.check('401 for guest and unsigned token')
    stack.call('/api/v1/employee/applications', 'client_good', expected=403)
    stack.call('/api/v1/client/applications', 'bank_employee', expected=403)
    stack.check('403 for cross-role access')
    for role, path in [('client_good', '/api/v1/client/applications/9223372036854775807'),
                       ('bank_employee', '/api/v1/employee/applications/9223372036854775807')]:
        stack.call(path, role, expected=404)
    stack.check('404 for absent application in both workspaces')
    stack.call('/api/v1/client/applications', 'client_good', expected=400, raw=b'{broken')
    stack.call('/api/v1/auth/demo-login', body={'userCode': 'unknown'}, expected=400)
    stack.call('/api/v1/employee/applications?status=UNKNOWN', 'bank_employee', expected=400)
    stack.call('/api/v1/client/applications', 'client_good',
               {'amount': 99_999, 'termMonths': 0, 'purpose': 'EQUIPMENT'}, 400)
    stack.call('/api/v1/client/applications', 'client_good',
               {'amount': 100_000.9, 'termMonths': 12, 'purpose': 'EQUIPMENT'}, 400)
    stack.call('/api/v1/client/applications', 'client_good',
               {'amount': 100_000, 'termMonths': 12, 'purpose': 'EQUIPMENT', 'ownerId': 2}, 400)
    stack.check('400 for malformed JSON, enums, fractional rubles, unknown fields and boundaries')
    _, _, denied_cors = stack.call('/api/health', expected=None, headers={'Origin': 'https://untrusted.invalid'})
    stack.check('CORS does not reflect untrusted origin', 'Access-Control-Allow-Origin' not in denied_cors)
    _, _, allowed_cors = stack.call('/api/health', headers={'Origin': 'http://localhost:3000'})
    stack.check('CORS allows configured exact origin', allowed_cors.get('Access-Control-Allow-Origin') == 'http://localhost:3000')

    good = stack.create()
    stack.check('creation returns durable PROCESSING and version zero', good['status'] == 'PROCESSING' and good['version'] == 0)
    stack.call(f'/api/v1/client/applications/{good["id"]}', 'client_bad', expected=403)
    stack.call(f'/api/v1/employee/applications/{good["id"]}', 'client_good', expected=403)
    stack.check('IDOR and client-to-dossier denial')
    approved = stack.wait(good)
    stack.check('client create -> mock providers -> scoring -> APPROVED', approved['status'] == 'APPROVED')
    stack.check('client refresh sees persisted final version', stack.get(f'/api/v1/client/applications/{good["id"]}', 'client_good') == approved)
    bad = stack.wait(stack.create('client_bad'), 'client_bad')
    stack.check('high-risk demo profile produces explained rejection', bad['status'] == 'REJECTED' and bool(bad['rejectionReason']))
    dossier = stack.dossier(good['id'])
    stack.check('real dossier contains snapshot, demo sources and four score components',
                dossier['demoOnly'] is True and len(dossier['sources']) == 3
                and all(source['demoOnly'] is True for source in dossier['sources'])
                and len(dossier['assessment']['components']) == 4 and len(dossier['snapshotSha256']) == 64)
    stack.decide(dossier, stack.command(dossier, 'REJECT'), 409)
    stack.check('terminal application cannot be arbitrarily changed')

    manual = stack.manual('APPROVE')
    command = stack.command(manual, 'APPROVE')
    invalid = {**command, 'rationale': ' '}
    stack.decide(manual, invalid, 400)
    competitors = [command, {**command, 'requestId': str(uuid.uuid4()), 'type': 'REJECT'}]
    endpoint = f'/api/v1/employee/applications/{manual["application"]["id"]}/decisions'
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda payload: stack.call(endpoint, 'bank_employee', payload, None), competitors))
    stack.check('concurrent decisions: exactly one 200 and one 409', sorted(result[0] for result in results) == [200, 409])
    winner = next(index for index, result in enumerate(results) if result[0] == 200)
    saved = results[winner][1]
    repeated = stack.decide(manual, competitors[winner])
    stack.check('exact replay returns recorded response', repeated == saved)
    stack.decide(manual, {**competitors[winner], 'rationale': 'Другой текст с тем же ключом'}, 409)
    actual = stack.dossier(manual['application']['id'])
    stack.check('decision replay creates no duplicate audit', len(actual['audit']) == len(manual['audit']) + 1)
    stack.check('client sees employee decision', stack.get(f'/api/v1/client/applications/{manual["application"]["id"]}', 'client_good')['status'] == saved['application']['status'])

    to_approve = stack.manual('APPROVE')
    accepted = stack.decide(to_approve, stack.command(to_approve, 'APPROVE'))
    stack.check('employee approves valid manual scenario with rationale', accepted['application']['status'] == 'APPROVED')
    to_reject = stack.manual('REJECT')
    rejected = stack.decide(to_reject, stack.command(to_reject, 'REJECT'))
    stack.check('employee rejects manual scenario with rationale', rejected['application']['status'] == 'REJECTED')

    to_offer = stack.manual('COUNTEROFFER')
    original = to_offer['application']['amount']
    cap = to_offer['assessment']['affordableAmount']
    amount = min(original - 100_000, cap)
    assert amount >= 100_000, 'seed must have an affordable reduced offer'
    stack.decide(to_offer, stack.command(to_offer, 'COUNTEROFFER', original + 1), 400)
    offer = stack.decide(to_offer, stack.command(to_offer, 'COUNTEROFFER', amount))['application']
    stack.check('employee counteroffer preserves original request and caps amount',
                offer['status'] == 'OFFER_PROPOSED' and offer['amount'] == original and offer['offer']['amount'] == amount)
    response = {'expectedVersion': offer['version'], 'requestId': str(uuid.uuid4()), 'decision': 'ACCEPT'}
    offer_path = f'/api/v1/client/applications/{offer["id"]}/offer-response'
    stack.call(offer_path, 'client_bad', response, 403)
    stack.call(offer_path, 'bank_employee', response, 403)
    final = stack.call(offer_path, 'client_good', response)[1]
    stack.check('owner accepts real demo offer', final['status'] == 'APPROVED' and final['version'] == offer['version'] + 1)
    stack.check('client consent is idempotent', stack.call(offer_path, 'client_good', response)[1] == final)
    stack.call(offer_path, 'client_good', {**response, 'decision': 'REJECT'}, 409)
    final_dossier = stack.dossier(offer['id'])
    stack.check('employee sees client consent and single audit event',
                final_dossier['application']['status'] == 'APPROVED'
                and len(final_dossier['audit']) == len(to_offer['audit']) + 2)

    offered = stack.wait(stack.create(amount=10_000_000))
    stack.check('automatic affordable smaller offer', offered['status'] == 'OFFER_PROPOSED' and 100_000 <= offered['offer']['amount'] < offered['amount'])
    decline = {'expectedVersion': offered['version'], 'requestId': str(uuid.uuid4()), 'decision': 'REJECT'}
    declined = stack.call(f'/api/v1/client/applications/{offered["id"]}/offer-response', 'client_good', decline)[1]
    stack.check('owner can decline automatic offer', declined['status'] == 'REJECTED')

    all_apps = stack.get('/api/v1/employee/applications', 'bank_employee')
    filtered = []
    for status in ('PROCESSING', 'MANUAL_REVIEW', 'OFFER_PROPOSED', 'APPROVED', 'REJECTED'):
        apps = stack.get('/api/v1/employee/applications?status=' + status, 'bank_employee')
        assert all(app['status'] == status for app in apps), status
        filtered.extend(apps)
    stack.check('omitted status returns all; all five filters work', {app['id'] for app in all_apps} == {app['id'] for app in filtered})
    search = stack.get('/api/v1/employee/applications?search=' + str(good['id']), 'bank_employee')
    stack.check('queue search reaches the backend', any(app['id'] == good['id'] for app in search))
    stack.check('audit event IDs and versions are unique', len({event['id'] for event in final_dossier['audit']}) == len(final_dossier['audit'])
                and len({event['version'] for event in final_dossier['audit']}) == len(final_dossier['audit']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:3000')
    parser.add_argument('--mode', choices=('smoke', 'create-pending', 'verify-recovered'), default='smoke')
    parser.add_argument('--state-file', type=Path, default=Path('/tmp/alfa-restart-smoke.json'))
    args = parser.parse_args()
    stack = Stack(args.base_url)
    stack.login()
    if args.mode == 'create-pending':
        app = stack.create()
        time.sleep(2)
        persisted = stack.get(f'/api/v1/client/applications/{app["id"]}', 'client_good')
        stack.check('worker-disabled application remains PROCESSING', persisted['status'] == 'PROCESSING')
        dossier = stack.dossier(app['id'])
        snapshot_hash = dossier.get('snapshotSha256')
        stack.check('pending job already has immutable input snapshot',
                    isinstance(snapshot_hash, str) and len(snapshot_hash) == 64)
        args.state_file.write_text(json.dumps({'id': app['id'], 'snapshotSha256': snapshot_hash}))
    elif args.mode == 'verify-recovered':
        verify_recovered(stack, args.state_file)
    else:
        smoke(stack)
    print(f'PASS: {stack.passed} real-stack assertions; no HTTP interception', flush=True)


if __name__ == '__main__':
    main()
