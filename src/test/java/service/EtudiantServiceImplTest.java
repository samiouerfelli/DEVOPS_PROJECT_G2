package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tn.esprit.tpfoyer.entity.Etudiant;
import tn.esprit.tpfoyer.repository.EtudiantRepository;
import tn.esprit.tpfoyer.service.EtudiantServiceImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EtudiantServiceImplTest {

    @Mock
    private EtudiantRepository etudiantRepository;

    @InjectMocks
    private EtudiantServiceImpl etudiantService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testAddEtudiant() {
        Etudiant etudiant = new Etudiant();
        // Assuming this Etudiant is valid
        when(etudiantRepository.save(any(Etudiant.class))).thenReturn(etudiant);

        Etudiant result = etudiantService.addEtudiant(etudiant);
        assertNotNull(result);
        verify(etudiantRepository, times(1)).save(etudiant);
    }

    @Test
    void testAddEtudiantWithInvalidData() {
        Etudiant etudiant = new Etudiant(); // Create an invalid Etudiant

        // Adjust the addEtudiant method to throw a specific exception for invalid data
        assertThrows(RuntimeException.class, () -> {
            etudiantService.addEtudiant(etudiant);
        });
    }

    @Test
    void testRetrieveAllEtudiants() {
        Etudiant etudiant1 = new Etudiant();
        Etudiant etudiant2 = new Etudiant();
        List<Etudiant> etudiants = Arrays.asList(etudiant1, etudiant2);

        when(etudiantRepository.findAll()).thenReturn(etudiants);

        List<Etudiant> result = etudiantService.retrieveAllEtudiants();
        assertEquals(2, result.size());
        verify(etudiantRepository, times(1)).findAll();
    }

    @Test
    void testRetrieveEtudiant() {
        Etudiant etudiant = new Etudiant();
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));

        Etudiant result = etudiantService.retrieveEtudiant(1L);
        assertNotNull(result);
        verify(etudiantRepository, times(1)).findById(1L);
    }

    @Test
    void testRetrieveEtudiantNotFound() {
        when(etudiantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            etudiantService.retrieveEtudiant(999L);
        });
        verify(etudiantRepository, times(1)).findById(999L);
    }

    @Test
    void testUpdateEtudiant() {
        Etudiant etudiant = new Etudiant();
        when(etudiantRepository.save(any(Etudiant.class))).thenReturn(etudiant);

        Etudiant result = etudiantService.modifyEtudiant(etudiant);
        assertNotNull(result);
        verify(etudiantRepository, times(1)).save(etudiant);
    }

    @Test
    void testUpdateEtudiantNotFound() {
        Etudiant etudiant = new Etudiant(); // Not found in DB
        when(etudiantRepository.save(any(Etudiant.class))).thenThrow(new RuntimeException("Etudiant not found"));

        assertThrows(RuntimeException.class, () -> {
            etudiantService.modifyEtudiant(etudiant);
        });
        verify(etudiantRepository, times(1)).save(etudiant);
    }

    @Test
    void testDeleteEtudiant() {
        Long id = 1L;
        doNothing().when(etudiantRepository).deleteById(id);

        etudiantService.removeEtudiant(id);
        verify(etudiantRepository, times(1)).deleteById(id);
    }

    @Test
    void testDeleteEtudiantNotFound() {
        Long id = 999L; // ID does not exist
        doThrow(new RuntimeException("Etudiant not found")).when(etudiantRepository).deleteById(id);

        assertThrows(RuntimeException.class, () -> {
            etudiantService.removeEtudiant(id);
        });
        verify(etudiantRepository, times(1)).deleteById(id);
    }

    @Test
    void testRetrieveEtudiantByCin() {
        Etudiant etudiant = new Etudiant();
        long cin = 12345678L;
        when(etudiantRepository.findEtudiantByCinEtudiant(cin)).thenReturn(etudiant);

        Etudiant result = etudiantService.recupererEtudiantParCin(cin);
        assertNotNull(result);
        verify(etudiantRepository, times(1)).findEtudiantByCinEtudiant(cin);
    }

    @Test
    void testRetrieveEtudiantByCinNotFound() {
        long cin = 99999999L; // CIN does not exist
        when(etudiantRepository.findEtudiantByCinEtudiant(cin)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> {
            etudiantService.recupererEtudiantParCin(cin);
        });
        verify(etudiantRepository, times(1)).findEtudiantByCinEtudiant(cin);
    }
}
