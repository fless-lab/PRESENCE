// Chaincode d'ancrage PRESENCE.
//
// Il enregistre la racine de Merkle de chaque séance clôturée et l'historique
// des corrections. Il ne contient aucune donnée personnelle.
package main

import (
	"encoding/json"
	"fmt"
	"regexp"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

// Ancrage est la valeur stockée pour une séance.
type Ancrage struct {
	Seance       string       `json:"seance"`
	Salle        int          `json:"salle"`
	Debut        string       `json:"debut"`
	Fin          string       `json:"fin"`
	Racine       string       `json:"racine"`
	NbEvenements int          `json:"nb_evenements"`
	Observateurs []string     `json:"observateurs"`
	Corrections  []Correction `json:"corrections"`
	Auteur       string       `json:"auteur"`
	Transaction  string       `json:"transaction"`
}

// Correction relie une nouvelle racine à la précédente, sans l'effacer.
type Correction struct {
	Racine           string `json:"racine"`
	RacinePrecedente string `json:"racine_precedente"`
	Motif            string `json:"motif"`
	Auteur           string `json:"auteur"`
	Transaction      string `json:"transaction"`
}

// ContratAncrage expose les fonctions du chaincode.
type ContratAncrage struct {
	contractapi.Contract
}

var formatRacine = regexp.MustCompile(`^[0-9a-f]{64}$`)

func cle(seance string) string { return "seance:" + seance }

// AncrerSeance enregistre la racine d'une séance. Un second ancrage du même identifiant est refusé.
func (c *ContratAncrage) AncrerSeance(ctx contractapi.TransactionContextInterface,
	seance string, salle int, debut string, fin string, racine string,
	nbEvenements int, observateursJSON string) error {

	if seance == "" {
		return fmt.Errorf("identifiant de séance vide")
	}
	if !formatRacine.MatchString(racine) {
		return fmt.Errorf("racine invalide : 64 caractères hexadécimaux attendus")
	}
	existant, err := ctx.GetStub().GetState(cle(seance))
	if err != nil {
		return err
	}
	if existant != nil {
		return fmt.Errorf("la séance %s est déjà ancrée", seance)
	}
	var observateurs []string
	if err := json.Unmarshal([]byte(observateursJSON), &observateurs); err != nil {
		return fmt.Errorf("liste d'observateurs invalide : %w", err)
	}
	auteur, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return err
	}
	a := Ancrage{
		Seance: seance, Salle: salle, Debut: debut, Fin: fin, Racine: racine,
		NbEvenements: nbEvenements, Observateurs: observateurs, Corrections: []Correction{},
		Auteur: auteur, Transaction: ctx.GetStub().GetTxID(),
	}
	valeur, err := json.Marshal(a)
	if err != nil {
		return err
	}
	return ctx.GetStub().PutState(cle(seance), valeur)
}

// AncrerCorrection ajoute une nouvelle racine à une séance déjà ancrée.
func (c *ContratAncrage) AncrerCorrection(ctx contractapi.TransactionContextInterface,
	seance string, nouvelleRacine string, racinePrecedente string, motif string) error {

	a, err := c.LireAncrage(ctx, seance)
	if err != nil {
		return err
	}
	if !formatRacine.MatchString(nouvelleRacine) {
		return fmt.Errorf("racine invalide")
	}
	courante := a.Racine
	if n := len(a.Corrections); n > 0 {
		courante = a.Corrections[n-1].Racine
	}
	if racinePrecedente != courante {
		return fmt.Errorf("la racine précédente ne correspond pas à la racine courante")
	}
	auteur, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return err
	}
	a.Corrections = append(a.Corrections, Correction{
		Racine: nouvelleRacine, RacinePrecedente: racinePrecedente, Motif: motif,
		Auteur: auteur, Transaction: ctx.GetStub().GetTxID(),
	})
	valeur, err := json.Marshal(a)
	if err != nil {
		return err
	}
	return ctx.GetStub().PutState(cle(seance), valeur)
}

// LireAncrage renvoie l'ancrage d'une séance.
func (c *ContratAncrage) LireAncrage(ctx contractapi.TransactionContextInterface, seance string) (*Ancrage, error) {
	valeur, err := ctx.GetStub().GetState(cle(seance))
	if err != nil {
		return nil, err
	}
	if valeur == nil {
		return nil, fmt.Errorf("aucun ancrage pour la séance %s", seance)
	}
	var a Ancrage
	if err := json.Unmarshal(valeur, &a); err != nil {
		return nil, err
	}
	return &a, nil
}

func main() {
	chaincode, err := contractapi.NewChaincode(&ContratAncrage{})
	if err != nil {
		panic(fmt.Sprintf("création du chaincode impossible : %v", err))
	}
	if err := chaincode.Start(); err != nil {
		panic(fmt.Sprintf("démarrage du chaincode impossible : %v", err))
	}
}
