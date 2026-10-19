/**
 * Layer and area rules for the frontend. The rules mirror the backend hexagonal layout:
 * domain is pure, application talks only to domain and its own ports, adapters sit at the edges,
 * and config is the only place that wires implementations together. See docs/frontend-architecture.md.
 */
const TEST = '(\\.test\\.tsx?$|^src/test-support/)';

module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      from: {},
      to: { circular: true },
    },
    {
      name: 'production-not-to-test',
      comment: 'Production code must not depend on tests or test support.',
      severity: 'error',
      from: { pathNot: TEST },
      to: { path: TEST },
    },
    {
      name: 'domain-is-pure',
      comment: 'domain imports only domain: no npm packages, no node built-ins, no other layers.',
      severity: 'error',
      from: { path: '^src/domain/', pathNot: TEST },
      to: { pathNot: '^src/domain/' },
    },
    {
      name: 'application-only-domain-and-ports',
      comment: 'application imports only domain and application code (no npm, no adapters, no config).',
      severity: 'error',
      from: { path: '^src/application/', pathNot: TEST },
      to: { pathNot: '^src/(domain|application)/' },
    },
    {
      name: 'application-area-isolation',
      comment:
        'An application area reaches other areas only through domain types (shared is the common kernel).',
      severity: 'error',
      from: { path: '^src/application/([^/]+)/', pathNot: TEST },
      to: { path: '^src/application/', pathNot: ['^src/application/$1/', '^src/application/shared/'] },
    },
    {
      name: 'ui-not-to-outbound',
      comment: 'UI calls use cases; it never reaches HTTP or memory adapters directly.',
      severity: 'error',
      from: { path: '^src/adapters/inbound/', pathNot: TEST },
      to: { path: '^src/adapters/outbound/' },
    },
    {
      name: 'ui-config-only-provider-hook',
      comment: 'UI may use only the services provider hook from config.',
      severity: 'error',
      from: { path: '^src/adapters/inbound/', pathNot: TEST },
      to: { path: '^src/config/', pathNot: '^src/config/ServicesContext\\.tsx?$' },
    },
    {
      name: 'outbound-only-ports-and-domain',
      comment: 'Outbound adapters implement ports; they do not use use cases, UI or config.',
      severity: 'error',
      from: { path: '^src/adapters/outbound/', pathNot: TEST },
      to: {
        path: '^src/(application|adapters/inbound|config)/',
        pathNot: '^src/application/[^/]+/ports/',
      },
    },
    {
      name: 'outbound-area-isolation',
      comment: "An outbound adapter of one area implements only that area's ports.",
      severity: 'error',
      from: { path: '^src/adapters/outbound/[^/]+/([^/]+)/', pathNot: TEST },
      to: {
        path: '^src/application/[^/]+/ports/',
        pathNot: ['^src/application/$1/ports/', '^src/application/shared/ports/'],
      },
    },
    {
      name: 'outbound-group-isolation',
      comment: 'Adapter groups (http, memory) are isolated from each other.',
      severity: 'error',
      from: { path: '^src/adapters/outbound/([^/]+)/', pathNot: TEST },
      to: { path: '^src/adapters/outbound/', pathNot: '^src/adapters/outbound/$1/' },
    },
    {
      name: 'only-config-wires',
      comment: 'Only config and the entry point may import config.',
      severity: 'error',
      from: { pathNot: ['^src/config/', '^src/main\\.tsx$', '^src/adapters/inbound/', TEST] },
      to: { path: '^src/config/' },
    },
    {
      name: 'not-to-unresolvable',
      severity: 'error',
      from: {},
      to: { couldNotResolve: true },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    tsPreCompilationDeps: true,
    tsConfig: { fileName: 'tsconfig.app.json' },
    enhancedResolveOptions: {
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'default', 'types'],
      mainFields: ['module', 'main', 'types', 'typings'],
    },
  },
};
