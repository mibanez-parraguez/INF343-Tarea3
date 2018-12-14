package tarea3;

public class Doctor extends Empleado{
	
	public boolean coordinador = false;
	
	// TODO static?
	public static void eleccion(){
	}
	
	public boolean esCoordinador(){
		return coordinador;
	}
	
	public String toString(){
		return String.format("Doctor[%02d]: %s %s (estudios: %2d, exp: %2d)", this.id, this.nombre, this.apellido, this.estudios, this.experiencia);
	}
}
