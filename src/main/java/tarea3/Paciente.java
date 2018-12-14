package tarea3;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Paciente {
	class Datos {
		@Expose
		public String nombre;
		@Expose
		public String rut;
		@Expose
		public int edad;
	}
	
	class Proc{
		@Expose
		public List<String> asignados = null;
		@Expose
		public List<String> completados = null;
	}
	
	class Exams{
		@Expose
		public List<String> realizados = null;
		@SerializedName("no realizados")
		@Expose
		public List<String> no_realizados = null;
	}
	
	class Drugs{
		@Expose
		public List<String> recetados = null;
		@Expose
		public List<String> suministrados = null;
	}
	
	@Expose
	public int paciente_id;
	@SerializedName("datos personales")
	@Expose
	public Datos[] datos_personales;
	@Expose
	public List<String> enfermedades = null;
	@SerializedName("tratamientos/procedimientos")
	@Expose
	public Proc[] procedimientos;
	@Expose
	public Exams[] examenes;
	@Expose
	public Drugs[] medicamentos;
	
	// No exposed =P
	public bool locked;
}
