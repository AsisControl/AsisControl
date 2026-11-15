package com.asiscontrol.repository;
import com.asiscontrol.entity.BeneficioEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface BeneficioEstudianteRepository extends JpaRepository<BeneficioEstudiante,Long> {
    List<BeneficioEstudiante> findAllByOrderByAnioAcademicoDescIdDesc();
    @Query("select b from BeneficioEstudiante b where b.activo=true and b.alumno.id=:alumno and b.concepto.id=:concepto and b.anioAcademico=:anio and b.mesInicio<=:fin and b.mesFin>=:inicio order by b.id")
    List<BeneficioEstudiante> superpuestos(@Param("alumno")Long alumno,@Param("concepto")Long concepto,@Param("anio")int anio,@Param("inicio")int inicio,@Param("fin")int fin);
}
