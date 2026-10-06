import { Navigate, Route, Routes } from 'react-router-dom';
import { Cadre } from './components/Cadre';
import { Protege, useAuth } from './lib/auth';
import { Apercu } from './pages/Apercu';
import { Audit } from './pages/Audit';
import { Connexion } from './pages/Connexion';
import { Personnes } from './pages/Personnes';
import { Salles } from './pages/Salles';
import { SeanceDetail } from './pages/SeanceDetail';
import { Seances } from './pages/Seances';
import { Verifier } from './pages/Verifier';

function VerifierPublic() {
  const { personne } = useAuth();
  return personne ? (
    <Cadre>
      <Verifier />
    </Cadre>
  ) : (
    <Verifier />
  );
}

export function App() {
  return (
    <Routes>
      <Route path="/connexion" element={<Connexion />} />
      <Route path="/verifier" element={<VerifierPublic />} />
      <Route
        element={
          <Protege>
            <Cadre />
          </Protege>
        }
      >
        <Route index element={<Apercu />} />
        <Route path="seances" element={<Seances />} />
        <Route path="seances/:id" element={<SeanceDetail />} />
        <Route path="personnes" element={<Personnes />} />
        <Route path="salles" element={<Salles />} />
        <Route path="audit" element={<Audit />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
