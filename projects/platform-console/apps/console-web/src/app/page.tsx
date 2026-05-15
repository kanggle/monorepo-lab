/**
 * Service-catalog home page — Phase 1 static placeholder.
 *
 * Tiles are hardcoded for the skeleton. In Phase 2 (TASK-PC-FE-001) this
 * page will:
 *   - Require an authenticated session (GAP OIDC Auth Code + PKCE).
 *   - Fetch the tile list from CONSOLE_REGISTRY_URL (data-driven catalog).
 *   - Show per-tenant available/unavailable state from the registry response.
 *
 * TODO(TASK-PC-FE-001): replace static tiles with data-driven catalog fetch.
 * TODO(TASK-PC-FE-001): add authentication guard (redirect to GAP login if unauthenticated).
 * TODO(TASK-PC-FE-001): add tenant switcher in the header.
 */

type ServiceTile = {
  id: string;
  label: string;
  description: string;
  available: boolean;
};

const TILES: ServiceTile[] = [
  {
    id: 'gap',
    label: 'Global Account Platform',
    description: 'Identity, tenants, OIDC, operator management',
    available: true,
  },
  {
    id: 'wms',
    label: 'WMS',
    description: 'Warehouse management — inbound, outbound, inventory',
    available: true,
  },
  {
    id: 'scm',
    label: 'SCM',
    description: 'Supply chain — procurement, suppliers, purchase orders',
    available: true,
  },
  {
    id: 'erp',
    label: 'ERP',
    description: 'Enterprise resource planning',
    available: false,
  },
  {
    id: 'finance',
    label: 'Finance',
    description: 'Financial platform',
    available: false,
  },
];

export default function HomePage() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Phase 1 skeleton banner */}
      <div className="mb-8 rounded-md border border-muted bg-muted px-4 py-3 text-sm text-muted-foreground">
        <strong>Skeleton</strong> — SSO, data-driven catalog, tenant switcher, and domain screens are
        implemented in <code className="font-mono text-xs">TASK-PC-FE-001</code> (Phase 2).
      </div>

      <h1 className="mb-6 text-2xl font-semibold">Services</h1>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TILES.map((tile) => (
          <ServiceCard key={tile.id} tile={tile} />
        ))}
      </div>
    </div>
  );
}

function ServiceCard({ tile }: { tile: ServiceTile }) {
  const base =
    'rounded-lg border p-5 transition-colors';
  const available =
    'border-border bg-background hover:border-primary cursor-pointer';
  const disabled =
    'border-border bg-muted opacity-60 cursor-not-allowed';

  return (
    <div className={`${base} ${tile.available ? available : disabled}`}>
      <div className="flex items-start justify-between gap-2">
        <h2 className="text-base font-medium">{tile.label}</h2>
        {!tile.available && (
          <span className="shrink-0 rounded-full bg-muted-foreground/20 px-2 py-0.5 text-xs text-muted-foreground">
            Coming soon
          </span>
        )}
      </div>
      <p className="mt-1 text-sm text-muted-foreground">{tile.description}</p>
      {/*
       * TODO(TASK-PC-FE-001): wire navigation → domain section (e.g. /gap, /wms, /scm).
       * Domain screens are rendered by the console calling each domain's gateway/admin API.
       */}
    </div>
  );
}
