package tarea3;

public class Enfermero extends Empleado{
// 	@Expose
// 	public int id;
// 	@Expose
// 	public String nombre;
// 	@Expose
// 	public String apellido;
// 	@Expose
// 	public int estudios;
// 	@Expose
// 	public int experiencia;

	public String toString(){
		return String.format("Enfermero[%02d]: %s %s (estudios: %2d, exp: %2d)", this.id, this.nombre, this.apellido, this.estudios, this.experiencia);
	}
} 
