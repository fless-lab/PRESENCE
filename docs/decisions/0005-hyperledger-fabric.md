# 0005. Registre Hyperledger Fabric à trois organisations

**Statut** : acceptée, octobre 2026

## Contexte

Le registre doit être partagé entre parties qui ne se font pas entièrement confiance, sans cryptomonnaie ni frais de transaction. Alternatives étudiées : Hyperledger Besu avec un contrat Solidity, ancrage sur une chaîne publique.

## Décision

Réseau Fabric à trois organisations (Scolarité, Direction, Service informatique), ordonnancement Raft, chaincode d'ancrage en Go, politique d'approbation 2 sur 3.

## Conséquences

- Le modèle d'organisations correspond directement à la gouvernance entre services.
- L'architecture reste indépendante du registre : seul le module d'ancrage du serveur dépend de Fabric. Un ancrage public reste possible pour une organisation unique.
