import type { ReactNode } from 'react';

export function EnTete({
  titre,
  sousTitre,
  actions,
  avant,
}: {
  titre: string;
  sousTitre?: ReactNode;
  actions?: ReactNode;
  avant?: ReactNode;
}) {
  return (
    <header className="entete">
      <div>
        {avant}
        <h1>{titre}</h1>
        {sousTitre && <p className="sous-titre">{sousTitre}</p>}
      </div>
      {actions && <div className="actions">{actions}</div>}
    </header>
  );
}
