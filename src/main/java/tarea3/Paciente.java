package tarea3;

import java.util.List;
import java.util.ArrayList;
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
		public ArrayList<String> asignados = null;
		@Expose
		public ArrayList<String> completados = null;
	}
	
	class Exams{
		@Expose
		public ArrayList<String> realizados = null;
		@SerializedName("no realizados")
		@Expose
		public ArrayList<String> no_realizados = null;
	}
	
	class Drugs{
		@Expose
		public ArrayList<String> recetados = null;
		@Expose
		public ArrayList<String> suministrados = null;
	}
	
	@Expose
	public int paciente_id;
	@SerializedName("datos personales")
	@Expose
	public Datos[] datos_personales;
	@Expose
	public ArrayList<String> enfermedades = null;
	@SerializedName("tratamientos/procedimientos")
	@Expose
	public Proc[] procedimientos;
	@Expose
	public Exams[] examenes;
	@Expose
	public Drugs[] medicamentos;
	
	// No exposed =P
	public boolean locked;
	public int id_req;
	public int hospital;
}
