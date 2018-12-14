package tarea3;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Iterator;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.gson.Gson;

public class Hospital {
	public static final String PACIENTES_FILE = "pacientes.json";
	public static final String REQUERIM_FILE = "requerimientos.json";
	public static final String STAFF_FILE = "staff.json";
	
	public String getGreeting() {
		return "Hello world.";
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		
		System.out.println("Hello!!");
		
		BufferedReader bufferedReader = new BufferedReader(new FileReader(PACIENTES_FILE));
		
		Paciente[] pacientes = new Gson().fromJson(bufferedReader, Paciente[].class);
		System.out.println("[1]" + pacientes[0].datos_personales[0].nombre);
		System.out.println("[2]" + pacientes[0].enfermedades.get(1));
		
		System.out.println("[3.1]" + pacientes[0].procedimientos[0].asignados.get(0));
		System.out.println("[3.3]" + pacientes[0].procedimientos[1].completados.get(0));
		
		System.out.println("[4.1]" + pacientes[0].examenes[1].no_realizados.get(0));
		System.out.println("[4.2]" + pacientes[0].examenes[0].realizados.get(0));
		
		System.out.println("[5.1]" + pacientes[0].medicamentos[0].recetados.get(0));
		System.out.println("[5.2]" + pacientes[0].medicamentos[1].suministrados.get(0));
	}
}
