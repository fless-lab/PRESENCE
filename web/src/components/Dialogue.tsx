import { X } from 'lucide-react';
import { useEffect, useRef, type ReactNode } from 'react';

interface Props {
  ouvert: boolean;
  titre: string;
  onFermer: () => void;
  children: ReactNode;
  pied?: ReactNode;
  large?: boolean;
}

/** Boîte de dialogue modale fondée sur l'élément natif <dialog>. */
export function Dialogue({ ouvert, titre, onFermer, children, pied, large = false }: Props) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (ouvert && !d.open) d.showModal();
    if (!ouvert && d.open) d.close();
  }, [ouvert]);

  return (
    <dialog
      ref={ref}
      className={`dialogue${large ? ' large' : ''}`}
      onClose={onFermer}
      onCancel={onFermer}
      onClick={(e) => {
        if (e.target === ref.current) onFermer();
      }}
      aria-label={titre}
    >
      {ouvert && (
        <>
          <div className="dialogue-entete">
            <h2>{titre}</h2>
            <button type="button" className="bouton bouton-icone" onClick={onFermer} aria-label="Fermer">
              <X size={16} />
            </button>
          </div>
          <div className="dialogue-corps">{children}</div>
          {pied && <div className="dialogue-pied">{pied}</div>}
        </>
      )}
    </dialog>
  );
}
