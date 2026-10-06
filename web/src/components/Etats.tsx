import { AlertCircle } from 'lucide-react';

export function Erreur({ message }: { message: string }) {
  return (
    <div className="alerte alerte-erreur" role="alert">
      <AlertCircle size={16} />
      <span>{message}</span>
    </div>
  );
}

export function Vide({ children }: { children: string }) {
  return <p className="vide">{children}</p>;
}

/** Lignes fantômes pendant le chargement d'un tableau. */
export function LignesSquelette({ colonnes, lignes = 4 }: { colonnes: number; lignes?: number }) {
  return (
    <>
      {Array.from({ length: lignes }, (_, i) => (
        <tr key={i} className="squelette" aria-hidden="true">
          {Array.from({ length: colonnes }, (_, j) => (
            <td key={j}>
              <span className="squelette-barre" style={{ width: `${40 + ((i * 7 + j * 13) % 45)}%` }} />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

export function Chargement() {
  return <p className="chargement-texte">Chargement…</p>;
}
