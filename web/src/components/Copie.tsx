import { Check, Copy } from 'lucide-react';
import { useEffect, useState } from 'react';

export function BoutonCopie({ texte, libelle = 'Copier' }: { texte: string; libelle?: string }) {
  const [copie, setCopie] = useState(false);
  useEffect(() => {
    if (!copie) return;
    const id = window.setTimeout(() => setCopie(false), 1500);
    return () => window.clearTimeout(id);
  }, [copie]);

  return (
    <button
      type="button"
      className="bouton bouton-icone"
      title={copie ? 'Copié' : libelle}
      aria-label={copie ? 'Copié' : libelle}
      onClick={() => {
        void navigator.clipboard?.writeText(texte).then(() => setCopie(true));
      }}
    >
      {copie ? <Check size={14} /> : <Copy size={14} />}
    </button>
  );
}

/** Empreinte abrégée en chasse fixe, valeur complète au survol. */
export function Empreinte({
  valeur,
  n = 10,
  copiable = false,
}: {
  valeur: string | null | undefined;
  n?: number;
  copiable?: boolean;
}) {
  if (!valeur) return <span className="tertiaire">–</span>;
  return (
    <span className="empreinte">
      <span className="mono" title={valeur}>
        {valeur.slice(0, n)}
      </span>
      {copiable && <BoutonCopie texte={valeur} libelle="Copier l'empreinte" />}
    </span>
  );
}
