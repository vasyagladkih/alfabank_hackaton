# Official Core Components source audit

This inventory records the actual documents and published files inspected during the design-system integration audit. It distinguishes official repository documentation from compiled package interfaces.

Requested documentation: https://core-ds.github.io/core-components/master/?path=/docs/intro--docs . Its client-rendered page returned no extractable text, so the matching official repository documentation and actual npm package tarballs were inspected.

Official repository: https://github.com/core-ds/core-components . A complete tree snapshot captured during the audit identified commit `1fa28dd0cb78b3975394e7ad14a602eb7b162ad2` on master. Documents below were fetched from the literal master URLs shown.

## Repository documents actually read

| Repository path | Exact fetched URL | Local read path |
| --- | --- | --- |
| `docs/installation.stories.mdx` | https://raw.githubusercontent.com/core-ds/core-components/master/docs/installation.stories.mdx | `/tmp/core-components-audit/docs/installation.stories.mdx` |
| `docs/theming.stories.mdx` | https://raw.githubusercontent.com/core-ds/core-components/master/docs/theming.stories.mdx | `/tmp/core-components-audit/docs/theming.stories.mdx` |
| `docs/typography.stories.mdx` | https://raw.githubusercontent.com/core-ds/core-components/master/docs/typography.stories.mdx | `/tmp/core-components-audit/docs/typography.stories.mdx` |
| `docs/core-config.stories.mdx` | https://raw.githubusercontent.com/core-ds/core-components/master/docs/core-config.stories.mdx | `/tmp/core-components-audit/docs/core-config.stories.mdx` |
| `docs/tables.stories.mdx` | https://raw.githubusercontent.com/core-ds/core-components/master/docs/tables.stories.mdx | `/tmp/core-components-audit/docs/tables.stories.mdx` |
| `.storybook/theming-wizard/answers/keepCssVars/index.tsx` | https://raw.githubusercontent.com/core-ds/core-components/master/.storybook/theming-wizard/answers/keepCssVars/index.tsx | `/tmp/core-components-audit/.storybook/theming-wizard/answers/keepCssVars/index.tsx` |
| `.storybook/theming-wizard/answers/keepCssVars/utils.ts` | https://raw.githubusercontent.com/core-ds/core-components/master/.storybook/theming-wizard/answers/keepCssVars/utils.ts | `/tmp/core-components-audit/.storybook/theming-wizard/answers/keepCssVars/utils.ts` |
| `.storybook/theming-wizard/config.ts` | https://raw.githubusercontent.com/core-ds/core-components/master/.storybook/theming-wizard/config.ts | `/tmp/core-components-audit/.storybook/theming-wizard/config.ts` |
| `.storybook/theming-wizard/index.tsx` | https://raw.githubusercontent.com/core-ds/core-components/master/.storybook/theming-wizard/index.tsx | `/tmp/core-components-audit/.storybook/theming-wizard/index.tsx` |
| `.storybook/theming-wizard/types.ts` | https://raw.githubusercontent.com/core-ds/core-components/master/.storybook/theming-wizard/types.ts | `/tmp/core-components-audit/.storybook/theming-wizard/types.ts` |
| `packages/vars/src/index.css` | https://raw.githubusercontent.com/core-ds/core-components/master/packages/vars/src/index.css | `/tmp/core-components-audit/packages/vars/src/index.css` |
| `packages/vars/src/bundle/corp.css` | https://raw.githubusercontent.com/core-ds/core-components/master/packages/vars/src/bundle/corp.css | `/tmp/core-components-audit/packages/vars/src/bundle/corp.css` |
| `packages/button/package.json` | https://raw.githubusercontent.com/core-ds/core-components/master/packages/button/package.json | `/tmp/core-components-audit/packages/button/package.json` |
| `packages/vars/package.json` | https://raw.githubusercontent.com/core-ds/core-components/master/packages/vars/package.json | `/tmp/core-components-audit/packages/vars/package.json` |
| `packages/table/package.json` | https://raw.githubusercontent.com/core-ds/core-components/master/packages/table/package.json | `/tmp/core-components-audit/packages/table/package.json` |
| `LICENSE.md` | https://raw.githubusercontent.com/core-ds/core-components/master/LICENSE.md | `/tmp/core-components-audit/LICENSE.md` |

## Published package files actually inspected

The registry URLs below identify the source tarball; paths listed beneath them are paths inside its `package/` directory. These are the compiled JavaScript, CSS, and declarations actually read, rather than inferred TypeScript source paths.

### @alfalab/core-components-button@13.2.4

Source artifact: https://registry.npmjs.org/@alfalab/core-components-button/-/core-components-button-13.2.4.tgz

- `/tmp/core-components-audit/published/button/package/package.json` — archive path `package/package.json`.
- `/tmp/core-components-audit/published/button/package/typings.d.ts` — archive path `package/typings.d.ts`.
- `/tmp/core-components-audit/published/button/package/esm/index.js` — archive path `package/esm/index.js`.
- `/tmp/core-components-audit/published/button/package/esm/components/base-button/Component.js` — archive path `package/esm/components/base-button/Component.js`.
- `/tmp/core-components-audit/published/button/package/esm/components/base-button/index.module.css.js` — archive path `package/esm/components/base-button/index.module.css.js`.
- `/tmp/core-components-audit/published/button/package/esm/components/base-button/default.css` — archive path `package/esm/components/base-button/default.css`.
- `/tmp/core-components-audit/published/button/package/esm/desktop/default.desktop.css` — archive path `package/esm/desktop/default.desktop.css`.

### @alfalab/core-components-input@17.2.2

Source artifact: https://registry.npmjs.org/@alfalab/core-components-input/-/core-components-input-17.2.2.tgz

- `/tmp/core-components-audit/published/input/package/Component.responsive.d.ts` — archive path `package/Component.responsive.d.ts`.
- `/tmp/core-components-audit/published/input/package/components/base-input/types/base-input-props.d.ts` — archive path `package/components/base-input/types/base-input-props.d.ts`.
- `/tmp/core-components-audit/published/input/package/esm/components/base-input/Component.js` — archive path `package/esm/components/base-input/Component.js`.

### @alfalab/core-components-select@19.2.11

Source artifact: https://registry.npmjs.org/@alfalab/core-components-select/-/core-components-select-19.2.11.tgz

- `/tmp/core-components-audit/published/select/package/index.d.ts` — archive path `package/index.d.ts`.
- `/tmp/core-components-audit/published/select/package/typings.d.ts` — archive path `package/typings.d.ts`.
- `/tmp/core-components-audit/published/select/package/esm/components/base-select/Component.js` — archive path `package/esm/components/base-select/Component.js`.
- `/tmp/core-components-audit/published/select/package/esm/components/field/Component.js` — archive path `package/esm/components/field/Component.js`.
- `/tmp/core-components-audit/published/select/package/esm/components/virtual-options-list/Component.js` — archive path `package/esm/components/virtual-options-list/Component.js`.

### @alfalab/core-components-typography@6.0.13

Source artifact: https://registry.npmjs.org/@alfalab/core-components-typography/-/core-components-typography-6.0.13.tgz

- `/tmp/core-components-audit/published/typography/package/title/component.d.ts` — archive path `package/title/component.d.ts`.
- `/tmp/core-components-audit/published/typography/package/text/component.d.ts` — archive path `package/text/component.d.ts`.
- `/tmp/core-components-audit/published/typography/package/colors.d.ts` — archive path `package/colors.d.ts`.
- `/tmp/core-components-audit/published/typography/package/esm/text/component.js` — archive path `package/esm/text/component.js`.
- `/tmp/core-components-audit/published/typography/package/esm/text/index.css` — archive path `package/esm/text/index.css`.
- `/tmp/core-components-audit/published/typography/package/esm/title/index.css` — archive path `package/esm/title/index.css`.

### @alfalab/core-components-table@4.0.30

Source artifact: https://registry.npmjs.org/@alfalab/core-components-table/-/core-components-table-4.0.30.tgz

- `/tmp/core-components-audit/published/table/package/component.d.ts` — archive path `package/component.d.ts`.
- `/tmp/core-components-audit/published/table/package/components/table/Component.d.ts` — archive path `package/components/table/Component.d.ts`.
- `/tmp/core-components-audit/published/table/package/components/thead/Component.d.ts` — archive path `package/components/thead/Component.d.ts`.
- `/tmp/core-components-audit/published/table/package/components/thead-cell/Component.d.ts` — archive path `package/components/thead-cell/Component.d.ts`.
- `/tmp/core-components-audit/published/table/package/components/tcell/Component.d.ts` — archive path `package/components/tcell/Component.d.ts`.
- `/tmp/core-components-audit/published/table/package/esm/components/table/Component.js` — archive path `package/esm/components/table/Component.js`.
- `/tmp/core-components-audit/published/table/package/esm/components/thead/Component.js` — archive path `package/esm/components/thead/Component.js`.

### @alfalab/core-components-plate@9.0.12

Source artifact: https://registry.npmjs.org/@alfalab/core-components-plate/-/core-components-plate-9.0.12.tgz

- `/tmp/core-components-audit/published/plate/package/components/base-plate/Component.d.ts` — archive path `package/components/base-plate/Component.d.ts`.

### @alfalab/core-components-status@5.1.1

Source artifact: https://registry.npmjs.org/@alfalab/core-components-status/-/core-components-status-5.1.1.tgz

- `/tmp/core-components-audit/published/status/package/Component.d.ts` — archive path `package/Component.d.ts`.
- `/tmp/core-components-audit/published/status/package/consts/index.d.ts` — archive path `package/consts/index.d.ts`.

### @alfalab/core-components-spinner@6.0.9

Source artifact: https://registry.npmjs.org/@alfalab/core-components-spinner/-/core-components-spinner-6.0.9.tgz

- `/tmp/core-components-audit/published/spinner/package/Component.d.ts` — archive path `package/Component.d.ts`.

### @alfalab/core-components-vars@11.4.1

Source artifact: https://registry.npmjs.org/@alfalab/core-components-vars/-/core-components-vars-11.4.1.tgz

- `/tmp/core-components-audit/published/vars/package/index.css` — archive path `package/index.css`.
- `/tmp/core-components-audit/published/vars/package/bundle/corp.css` — archive path `package/bundle/corp.css`.
- `/tmp/core-components-audit/published/vars/package/typography.css` — archive path `package/typography.css`.
- `/tmp/core-components-audit/published/vars/package/common.css` — archive path `package/common.css`.

### @alfalab/core-components-themes@15.2.1

Source artifact: https://registry.npmjs.org/@alfalab/core-components-themes/-/core-components-themes-15.2.1.tgz

- `/tmp/core-components-audit/published/themes/package/corp.css` — archive path `package/corp.css`.
- `/tmp/core-components-audit/published/themes/package/click.css` — archive path `package/click.css`.

### @alfalab/core-components-config@1.1.0

Source artifact: https://registry.npmjs.org/@alfalab/core-components-config/-/core-components-config-1.1.0.tgz

- `/tmp/core-components-audit/published/config/package/esm/CoreConfigContext.js` — archive path `package/esm/CoreConfigContext.js`.
- `/tmp/core-components-audit/published/config/package/esm/index.js` — archive path `package/esm/index.js`.

### @alfalab/core-components-stack-context@1.0.1

Source artifact: https://registry.npmjs.org/@alfalab/core-components-stack-context/-/core-components-stack-context-1.0.1.tgz

- `/tmp/core-components-audit/published/stack-context/package/esm/StackContext.js` — archive path `package/esm/StackContext.js`.
- `/tmp/core-components-audit/published/stack-context/package/esm/index.js` — archive path `package/esm/index.js`.

### @alfalab/core-components-mq@6.0.9

Source artifact: https://registry.npmjs.org/@alfalab/core-components-mq/-/core-components-mq-6.0.9.tgz

- `/tmp/core-components-audit/published/core-components-mq/package/esm/useIsDesktop.js` — archive path `package/esm/useIsDesktop.js`.
- `/tmp/core-components-audit/published/core-components-mq/package/esm/useMatchMedia.js` — archive path `package/esm/useMatchMedia.js`.

## Findings grounded in those files

- Separate `@alfalab/core-components-*` packages are explicitly documented. Normal ESM imports bring compiled component CSS.
- Published `@alfalab/core-components-themes@15.2.1/corp.css` contains the runtime token layer and corporate overrides. Its 1,533 distinct custom properties include colors, gaps, radii, and font families. It contains no unprocessed mixin directives.
- Raw vars bundle imports pull in `@define-mixin` source. The integration therefore imports the compiled corporate theme directly, after the component module tree so component default variables do not overwrite corporate overrides.
- Current config and stack-context packages provide defaults; a custom root provider is optional. The old CoreConfig documentation mentions `@alfalab/core-config`, while current package code uses `@alfalab/core-components-config`.
- Button converts `href` to `to` for a non-string custom Component, supporting a router Link directly.
- Typography.Title accepts `font="system"`; Typography.Text uses the system family by default and does not accept `font="system"` in its declaration.
- Input callbacks receive `(event, { value })`; Select callbacks receive an option payload with `selected`. Table.THead generates its own table row. Spinner visibility defaults false. Plate supplies role="alert" internally; application wrappers must not add duplicate live regions (confirmed during browser verification).
- Direct Core package peer ranges include React 19, but transitive react-virtual@2.10.4 declares React 16/17. The original audit reported that mismatch rather than claiming the whole graph supported React 19. The current installed dependency inventory is recorded separately and reflects the final lockfile, not the originally proposed stack.

## Font metadata actually inspected

The two official Storybook WOFF2 files below were downloaded only into temporary audit storage. Their name tables were read using the system FreeType library; no font assets were added to the repository. The application uses system fonts.

### styrene-ui_regular.woff2

Fetched source: https://raw.githubusercontent.com/core-ds/core-components/master/.storybook/public/fonts/styrene-ui_regular.woff2

Local read path: `/tmp/core-components-audit/styrene-ui_regular.woff2`.

SHA-256: `2125296d9d97fd8eacad5d014771909487cbadec236a65767ef62e4e46b5c7e9`.

- Name ID 0: Copyright (C)2016 Commercial Type.
- Name ID 8: Commercial Type, Inc.
- Name ID 9: Berton Hasebe
- Name ID 11: http://commercialtype.com
- Name ID 13: Not to be used for anything other than web font use!
- Name ID 14: http://commercialtype.com/license

### alfa-interface-sans_regular.woff2

Fetched source: https://raw.githubusercontent.com/core-ds/core-components/master/.storybook/public/fonts/alfa-interface-sans_regular.woff2

Local read path: `/tmp/core-components-audit/alfa-interface-sans_regular.woff2`.

SHA-256: `d01f0c2f390e7c373693660a0170189420fc0afe622ef55e3322e570c13e3a3b`.

- Name ID 0: Copyright © 2024, ParaType Ltd. All rights reserved.
- Name ID 8: ParaType Ltd.
- Name ID 9: Vitaly Kuzmin, Nick Nedashkovskiy
- Name ID 11: http://www.paratype.com
- Name ID 13: http://www.paratype.com/eula
- Name ID 14: http://www.paratype.com/eula

The MIT license for the component code was not treated as proof of separate font redistribution rights. The actual metadata is retained in `FONT_METADATA.json`.

## Final FormControl integration audit

The final browser test found three actual accessibility defects in rendered Select 19.2.11: an unnamed outer combobox, `aria-controls` referring to a non-mounted closed menu, and an arrow SVG with no accessible alternative. Its public Field/Arrow slots do not supply a name to that outer root. The application therefore uses public FormControl 14.0.11 with a native select. It does not describe the native select as Core Select or patch private library markup.

Additional official repository documents read directly through GitHub:

- https://github.com/core-ds/core-components/blob/master/packages/form-control/src/docs/description.mdx
- https://github.com/core-ds/core-components/blob/master/packages/form-control/src/docs/development.mdx
- https://github.com/core-ds/core-components/blob/master/packages/form-control/src/docs/Component.stories.tsx

The actual installed API, implementation and styles inspected are listed below relative to `frontend/node_modules/@alfalab/core-components-form-control/`:

- `package.json`
- `index.d.ts`
- `Component.responsive.d.ts`
- `components/base-form-control/Component.d.ts`
- `esm/components/base-form-control/Component.js`
- `components/base-form-control/index.css`
- `esm/components/base-form-control/index.css`
- `esm/components/base-form-control/default.css`
- `esm/desktop/default.desktop.css`
- `desktop/desktop.css`
- `mobile/mobile.css`

Source archive: https://registry.npmjs.org/@alfalab/core-components-form-control/-/core-components-form-control-14.0.11.tgz . The native-select composition is based on these public props, rather than presented as a copied Storybook example. `labelView="outer"` accepts a real `<label htmlFor>`, error content supplies a referenced ID, and focused/filled/disabled states are explicitly controlled. Native keyboard behavior and naming were exercised in actual Chromium.

Additional installed files inspected during composition, contrast and ARIA debugging, relative to `frontend/node_modules/`:

- `@alfalab/core-components-table/components/table/utils.js`
- `@alfalab/core-components-table/components/thead/Component.js`
- `@alfalab/core-components-select/typings.d.ts`
- `@alfalab/core-components-select/index.d.ts`
- `@alfalab/core-components-select/esm/components/base-select/Component.js`
- `@alfalab/core-components-select/components/field/Component.d.ts`
- `@alfalab/core-components-select/esm/components/field/Component.js`
- `@alfalab/core-components-select/esm/mobile/Component.mobile.js`
- `@alfalab/core-components-button/typings.d.ts`
- `@alfalab/core-components-button/esm/desktop/default.desktop.css`
- `@alfalab/core-components-button/esm/components/base-button/default.css`
- `@alfalab/core-components-button/esm/components/base-button/index.css`
- `@alfalab/core-components-status/index.css`

Final application components are Button, Input, FormControl, Typography, Plate, Status, Spinner and Table with official glyph icons. Core Select remains in the installed Table/Pagination dependency closure. The theme, dependency, license and actual test results are recorded in `../VERIFICATION.md`.
