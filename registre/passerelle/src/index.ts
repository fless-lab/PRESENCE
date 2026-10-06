/**
 * Passerelle PRESENCE vers Hyperledger Fabric.
 *
 * Le serveur Python appelle cette passerelle en HTTP ; elle soumet les transactions au
 * chaincode d'ancrage avec le SDK officiel Fabric Gateway. Les chemins par défaut
 * correspondent au réseau de test de fabric-samples (registre/reseau/README.md).
 */
import * as grpc from '@grpc/grpc-js';
import { connect, hash, signers, type Contract } from '@hyperledger/fabric-gateway';
import { createPrivateKey } from 'node:crypto';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { creerServeur, type Contrat } from './http.js';

const reseau = path.resolve(
  process.env.FABRIC_ORGANISATIONS ??
    '../reseau/fabric-samples/test-network/organizations/peerOrganizations/org1.example.com',
);
const env = {
  port: Number(process.env.PASSERELLE_PORT ?? 8800),
  canal: process.env.FABRIC_CANAL ?? 'presence',
  chaincode: process.env.FABRIC_CHAINCODE ?? 'ancrage',
  msp: process.env.FABRIC_MSP ?? 'Org1MSP',
  pair: process.env.FABRIC_PAIR ?? 'localhost:7051',
  hotePair: process.env.FABRIC_HOTE_PAIR ?? 'peer0.org1.example.com',
  certificat:
    process.env.FABRIC_CERTIFICAT ??
    path.join(reseau, 'users/User1@org1.example.com/msp/signcerts'),
  cle: process.env.FABRIC_CLE ?? path.join(reseau, 'users/User1@org1.example.com/msp/keystore'),
  tls: process.env.FABRIC_TLS ?? path.join(reseau, 'peers/peer0.org1.example.com/tls/ca.crt'),
};

async function premierFichier(chemin: string): Promise<Buffer> {
  const stat = await fs.stat(chemin);
  if (stat.isFile()) return fs.readFile(chemin);
  const fichiers = await fs.readdir(chemin);
  if (fichiers.length === 0) throw new Error(`aucun fichier dans ${chemin}`);
  return fs.readFile(path.join(chemin, fichiers[0]));
}

function adapter(contract: Contract): Contrat {
  return {
    async soumettre(fonction, args) {
      const envoi = await contract.submitAsync(fonction, { arguments: args });
      const statut = await envoi.getStatus();
      if (!statut.successful) {
        throw new Error(`transaction ${statut.transactionId} refusée (code ${statut.code})`);
      }
      return envoi.getTransactionId();
    },
    async evaluer(fonction, args) {
      return contract.evaluateTransaction(fonction, ...args);
    },
  };
}

async function principal(): Promise<void> {
  const tls = await premierFichier(env.tls);
  const client = new grpc.Client(env.pair, grpc.credentials.createSsl(tls), {
    'grpc.ssl_target_name_override': env.hotePair,
  });
  const gateway = connect({
    client,
    identity: { mspId: env.msp, credentials: await premierFichier(env.certificat) },
    signer: signers.newPrivateKeySigner(createPrivateKey(await premierFichier(env.cle))),
    hash: hash.sha256,
    evaluateOptions: () => ({ deadline: Date.now() + 5_000 }),
    endorseOptions: () => ({ deadline: Date.now() + 15_000 }),
    submitOptions: () => ({ deadline: Date.now() + 5_000 }),
    commitStatusOptions: () => ({ deadline: Date.now() + 60_000 }),
  });
  const contract = gateway.getNetwork(env.canal).getContract(env.chaincode);
  creerServeur(adapter(contract)).listen(env.port, () => {
    console.log(`Passerelle PRESENCE sur le port ${env.port}, canal ${env.canal}, ${env.msp}`);
  });
  const arreter = () => {
    gateway.close();
    client.close();
    process.exit(0);
  };
  process.on('SIGINT', arreter);
  process.on('SIGTERM', arreter);
}

principal().catch((e) => {
  console.error(e);
  process.exit(1);
});
