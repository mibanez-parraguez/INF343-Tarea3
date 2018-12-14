package tarea3;

public class Enfermero extends Empleado{
	public String toString(){
		return String.format("Enfermero[%02d]: %s %s (estudios: %2d, exp: %2d)", this.id, this.nombre, this.apellido, this.estudios, this.experiencia);
	}
} 
