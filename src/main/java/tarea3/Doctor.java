package tarea3;

import java.util.List;
import java.util.ArrayList;

public class Doctor extends Empleado{
	
	public boolean coordinador = false;
	public boolean enEleccion = false;
	public Coordinacion coordina = null;
	
	// TODO static?
	public static void eleccion(){
	}
	
	public boolean esCoordinador(){
		return this.coordinador;
	}
	
	public String toString(){
		return String.format("Doctor[%02d]: %s %s (estudios: %2d, exp: %2d, coordinador: %b)", this.id, this.nombre, this.apellido, this.estudios, this.experiencia, this.coordinador);
	}
	
	public void asumeCoordinacion() {
		if(!this.esCoordinador()){
			this.coordina = new Coordinacion();
			this.coordinador = true;
		}
	}
}

class Coordinacion {
	class MyQueue{
		public int staff_id;
		public String staff_cargo;
		public String maquina; //
		public int paciente_id;
		public int requerimiento_id;
	}
	
	public List<MyQueue> aqueue = null;
	
	Coordinacion(){
		this.aqueue = new ArrayList<MyQueue>();
	}
}
