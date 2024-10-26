package tn.esprit.tpfoyer.services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.tpfoyer.entities.ChambreDTO;
import tn.esprit.tpfoyer.entities.EtudiantDTO;
import tn.esprit.tpfoyer.entities.Reservation;
import tn.esprit.tpfoyer.entities.ReservationDTO;
import tn.esprit.tpfoyer.exception.ReservationException;
import tn.esprit.tpfoyer.feignclient.ChambreClient;
import tn.esprit.tpfoyer.feignclient.EtudiantClient;
import tn.esprit.tpfoyer.repository.ReservationRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ReservationServiceImpl  {

    ReservationRepository reservationRepository;
    private final EtudiantClient etudiantClient;
    private final ChambreClient chambreClient;


    private ReservationDTO convertToDto(Reservation reservation) {
        ReservationDTO reservationDTO = new ReservationDTO();
        reservationDTO.setIdReservation(reservation.getIdReservation());
        reservationDTO.setAnneeUniversitaire(reservation.getAnneeUniversitaire());
        reservationDTO.setEstValide(reservation.isEstValide());
        reservationDTO.setIdEtudiant(reservation.getIdEtudiant());
        reservationDTO.setIdChambre(reservation.getIdChambre());
        return reservationDTO;
    }

    public Reservation createReservation(Long idEtudiant, Long idChambre, Date anneeUniversitaire) {
        EtudiantDTO etudiant = etudiantClient.getEtudiantById(idEtudiant);
        if (etudiant == null) {
            throw new ReservationException("Etudiant not found with ID: " + idEtudiant);
        }

        ChambreDTO chambre = chambreClient.getChambreById(idChambre);
        if (chambre == null) {
            throw new ReservationException("Chambre not found with ID: " + idChambre);
        }

        Optional<Reservation> existingReservationForEtudiant = reservationRepository
                .findByIdEtudiantAndAnneeUniversitaireAndEstValideTrue(idEtudiant, anneeUniversitaire);
        if (existingReservationForEtudiant.isPresent()) {
            throw new ReservationException("Etudiant already has an active reservation for the selected academic year");
        }

        int chambreReservationCount = reservationRepository.countByChambreAndAnneeUniversitaire(idChambre, anneeUniversitaire);
        if (chambreReservationCount >= 2) {
            throw new ReservationException("Chambre already has the maximum number of reservations for the selected academic year");
        }

        Reservation reservation = new Reservation();
        reservation.setIdReservation(java.util.UUID.randomUUID().toString());
        reservation.setAnneeUniversitaire(anneeUniversitaire);
        reservation.setEstValide(true);
        reservation.setIdEtudiant(idEtudiant);
        reservation.setIdChambre(idChambre);

        Reservation savedReservation = reservationRepository.save(reservation);
        etudiant.getIdReservations().add(savedReservation.getIdReservation());
        etudiantClient.updateEtudiantReservations(idEtudiant, etudiant.getIdReservations());

        chambre.getIdReservations().add(reservation.getIdReservation());
        chambreClient.updateChambreReservations(idChambre, chambre.getIdReservations());


        return savedReservation;
    }

    public void cancelReservation(String idReservation) {
        Reservation reservation = reservationRepository.findById(idReservation)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        reservation.setEstValide(false);
        reservationRepository.save(reservation);

        Long idChambre = reservation.getIdChambre();
        Date anneeUniversitaire = reservation.getAnneeUniversitaire();

        int remainingReservations = reservationRepository.countByChambreAndAnneeUniversitaire(idChambre, anneeUniversitaire);

        if (remainingReservations < 2) {
            chambreClient.updateChambreAvailability(idChambre, false);
        }

        EtudiantDTO etudiant = etudiantClient.getEtudiantById(reservation.getIdEtudiant());
        etudiant.getIdReservations().remove(idReservation);
        etudiantClient.updateEtudiantReservations(reservation.getIdEtudiant(), etudiant.getIdReservations());

        ChambreDTO chambre = chambreClient.getChambreById(idChambre);
        chambre.getIdReservations().remove(idReservation);
        chambreClient.updateChambreReservations(idChambre, chambre.getIdReservations());

    }

    public Reservation getReservationById(String idReservation) {
        return reservationRepository.findById(idReservation)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
    }

    public List<Reservation> getReservationsByEtudiant(Long idEtudiant) {
        List<Reservation> reservations = reservationRepository.findByIdEtudiant(idEtudiant);

        reservations.forEach(this::convertToDto);

        return reservations;
    }


    public List<Reservation> getReservationsByChambreAndAnnee(Long idChambre, Date anneeUniversitaire) {
        List<Reservation> reservations = reservationRepository
                .findByIdChambreAndAnneeUniversitaireAndEstValideTrue(idChambre, anneeUniversitaire);

        reservations.forEach(this::convertToDto);

        return reservations;
    }





}