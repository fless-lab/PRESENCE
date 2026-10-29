import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { FournisseurAuth } from './lib/auth';
import { FournisseurPromotion } from './lib/promotion';
import './styles/tokens.css';
import './styles/base.css';
import './styles/mise-en-page.css';
import './styles/composants.css';
import './styles/seance.css';
import './styles/planning.css';

const racine = document.getElementById('racine');
if (!racine) throw new Error('Élément #racine introuvable');

createRoot(racine).render(
  <StrictMode>
    <BrowserRouter>
      <FournisseurAuth>
        <FournisseurPromotion>
          <App />
        </FournisseurPromotion>
      </FournisseurAuth>
    </BrowserRouter>
  </StrictMode>,
);
