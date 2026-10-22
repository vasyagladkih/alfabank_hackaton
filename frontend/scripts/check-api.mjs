import { readFile } from 'node:fs/promises';
import openapiTS, { astToString, COMMENT_HEADER } from 'openapi-typescript';

const schema = new URL('../../docs/openapi.yaml', import.meta.url);
const output = new URL('../src/api/schema.d.ts', import.meta.url);
const generated = COMMENT_HEADER + astToString(await openapiTS(schema));
const committed = await readFile(output, 'utf8');
if (generated !== committed) {
  console.error('API types are out of date. Run npm run generate:api and commit src/api/schema.d.ts.');
  process.exitCode = 1;
} else {
  console.log('API types match docs/openapi.yaml.');
}
