/**
 * Serveur HTTP de la passerelle. Il ne dépend que de l'interface Contrat,
 * ce qui permet de le tester sans réseau Fabric.
 */
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';

export interface Contrat {
  soumettre(fonction: string, args: string[]): Promise<string>; // renvoie l'identifiant de transaction
  evaluer(fonction: string, args: string[]): Promise<Uint8Array>;
}

export class ErreurChaincode extends Error {}

const RACINE = /^[0-9a-f]{64}$/;

function envoyer(res: ServerResponse, code: number, corps: unknown): void {
  const texte = JSON.stringify(corps);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(texte);
}

async function lireCorps(req: IncomingMessage): Promise<Record<string, unknown>> {
  const morceaux: Buffer[] = [];
  for await (const m of req) morceaux.push(m as Buffer);
  const texte = Buffer.concat(morceaux).toString('utf8');
  return texte ? (JSON.parse(texte) as Record<string, unknown>) : {};
}

function texte(o: Record<string, unknown>, cle: string): string {
  const v = o[cle];
  if (typeof v !== 'string' || v === '') throw new RangeError(`champ ${cle} attendu`);
  return v;
}

function codeErreur(e: unknown): number {
  const message = e instanceof Error ? e.message : String(e);
  if (/déjà ancrée|ne correspond pas/.test(message)) return 409;
  if (/aucun ancrage/.test(message)) return 404;
  if (e instanceof RangeError || e instanceof SyntaxError) return 422;
  return 502;
}

export function creerServeur(contrat: Contrat): Server {
  return createServer(async (req, res) => {
    try {
      const url = new URL(req.url ?? '/', 'http://passerelle');
      if (req.method === 'GET' && url.pathname === '/sante') {
        return envoyer(res, 200, { statut: 'ok' });
      }
      if (req.method === 'POST' && url.pathname === '/ancrages') {
        const c = await lireCorps(req);
        const racine = texte(c, 'racine');
        if (!RACINE.test(racine)) throw new RangeError('racine invalide');
        const transaction = await contrat.soumettre('AncrerSeance', [
          texte(c, 'seance'),
          String(c.salle ?? ''),
          String(c.debut ?? ''),
          String(c.fin ?? ''),
          racine,
          String(c.nb_evenements ?? 0),
          JSON.stringify(c.observateurs ?? []),
        ]);
        return envoyer(res, 200, { transaction });
      }
      if (req.method === 'POST' && url.pathname === '/corrections') {
        const c = await lireCorps(req);
        const transaction = await contrat.soumettre('AncrerCorrection', [
          texte(c, 'seance'),
          texte(c, 'nouvelle_racine'),
          texte(c, 'racine_precedente'),
          String(c.motif ?? ''),
        ]);
        return envoyer(res, 200, { transaction });
      }
      const m = /^\/ancrages\/(.+)$/.exec(url.pathname);
      if (req.method === 'GET' && m) {
        const brut = await contrat.evaluer('LireAncrage', [decodeURIComponent(m[1])]);
        return envoyer(res, 200, JSON.parse(Buffer.from(brut).toString('utf8')));
      }
      envoyer(res, 404, { detail: 'route inconnue' });
    } catch (e) {
      envoyer(res, codeErreur(e), { detail: e instanceof Error ? e.message : String(e) });
    }
  });
}
