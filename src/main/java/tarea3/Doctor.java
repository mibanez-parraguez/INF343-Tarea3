package tarea3;

import java.util.List;
import com.google.gson.annotations.Expose;

public class Doctor{
	@Expose
	public int id;
	@Expose
	public String nombre;
	@Expose
	public String apellido;
	@Expose
	public int estudios;
	@Expose
	public int experiencia;
	
	public boolean coordinador = false;
	
	// TODO static?
	public static void eleccion(){
	}
	
	public boolean esCoordinador(){
		return coordinador;
	}
}
