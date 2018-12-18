package tarea3;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.StatusRuntimeException;

import com.google.protobuf.Empty;

public class Doctor extends Empleado{
	
	public boolean coordinador = false;
	public boolean enEleccion = false;
	public Coordinacion coordinacion = null;
	
	private Paciente[] pacientes = null; // Asume pacientes ordenados por id
	private static Hospital.ConfigH config;
	
	public boolean esCoordinador(){
		return this.coordinador;
	}
	
	public String toString(){
		return String.format("Doctor[%02d]: %s %s (estudios: %2d, exp: %2d, coordinador: %b)", this.id, this.nombre, this.apellido, this.estudios, this.experiencia, this.coordinador);
	}
	
	public void asumeCoordinacion(Paciente[] pacientes, Hospital.ConfigH config) {
		if(!this.esCoordinador()){
			this.coordinacion = new Coordinacion(pacientes.length);
			this.pacientes = pacientes;
			this.coordinador = true;
			this.config = config;
		}
	}

	public int solicitaFicha(SolicitarMsg request){
		int id = request.getIdPaciente();
		if(this.pacientes[id].locked){
			this.coordinacion.ponEnQueue(request);
			return 2;
		}
		int id_req = request.getIdRequerimiento();
		int hospital = request.getHospital();
		this.lockPaciente(id, id_req, hospital);
		return 1; // 
	}
	
	
	public int modificaFicha(RequerimientoMsg request){
		int status;
		
		status = this.hazModificacion(request);
		this.autorizaSiguiente(request.getIdPaciente());
		return status;
	}
	
	public void autorizaSiguiente(int id_paciente){
		SolicitarMsg req = this.coordinacion.getSiguiente(id_paciente);
		int id_req = req.getIdRequerimiento();
		int hospital = req.getHospital();
		SolicitudOk.Builder msg = SolicitudOk.newBuilder();
		msg.setStatus(1);
		msg.setIdRequerimiento(id_req);
		msg.setHospital(hospital);
		
		this.lockPaciente(id_paciente, id_req, hospital);
		// todo crear cliente y mandar msge
	}

// diagnosticar / curar -> enfermedades
// asignar / poner /completar -> tratamientos/procedimientos 
// pedir / realizar -> examenes 
// recetar / suministrar -> medicamentos.
	
	public int hazModificacion(RequerimientoMsg request){
		
		if(!this.estaAutorizado(request)){
			return 2;
		}
		int id = request.getIdPaciente();
		String op = request.getReqData().split(" ")[0];
		String accion = request.getReqData().split(" ")[1];
		Paciente p = this.pacientes[id];
		
		// Enfermedades
		if(op.equals("diagnosticar")){
			p.enfermedades.add(accion);
			return 1;
		}
		if(op.equals("curar")){
			int ix = p.enfermedades.indexOf(accion);
			p.enfermedades.remove(ix);
			return 1;
		}
		// Tratamientos/Procedimientos
		if(op.equals("asignar")){
			p.procedimientos[0].asignados.add(accion);
			return 1;
		}
		if(op.equals("poner")){
			p.procedimientos[1].completados.add(accion);
			return 1;
		}
		if(op.equals("completar")){
			int ix = p.procedimientos[0].asignados.indexOf(accion);
			if(ix>-1){
				p.procedimientos[0].asignados.remove(ix);
			}
			p.procedimientos[1].completados.add(accion);
			return 1;
		}
		// Examenes
		if(op.equals("pedir")){
			p.examenes[1].no_realizados.add(accion);
			return 1;
		}
		if(op.equals("realizar")){
			int ix = p.examenes[1].no_realizados.indexOf(accion);
			if(ix>-1){
				p.examenes[1].no_realizados.remove(ix);
			}
			p.examenes[0].realizados.add(accion);
			return 1;
		}
		
		// Medicamentos
		if(op.equals("recetar")){
			p.medicamentos[0].recetados.add(accion);
			return 1;
		}
		if(op.equals("suministrar")){
			int ix = p.medicamentos[0].recetados.indexOf(accion);
			if(ix>-1){
				p.medicamentos[0].recetados.remove(ix);
			}
			p.medicamentos[1].suministrados.add(accion);
			return 1;
		}
		return 1;
	}

	private boolean estaAutorizado(RequerimientoMsg request){
		String cargo = request.getEmpleado().getCargo();
		String op = request.getReqData().split(" ")[0];
		if(cargo.equals("enfermero")){
			if(op.equals("completar"))
				return true;
			if(op.equals("poner")) // puede o solo el medico?
				return true;
			if(op.equals("suministrar"))
				return true;
			return false;
		}
		if(cargo.equals("paramedico")){
			if(op.equals("realizar"))
				return true;
			return false;
		}
		return true;
	}

	private boolean esElSiguiente(RequerimientoMsg request){
		// TODO
		int id = request.getIdPaciente();
		if(this.pacientes[id].id_req != request.getIdRequerimiento())
			return false;
		if(this.pacientes[id].hospital != request.getHospital())
			return false;
		return true;
	}
	
	private void lockPaciente(int id, int id_req, int hospital){
		this.pacientes[id].locked = true;
		this.pacientes[id].id_req = id_req;
		this.pacientes[id].hospital = hospital;
	}
	
	private void unlockPaciente(int id){
		this.pacientes[id].locked = false;
		this.pacientes[id].id_req = 0;
		this.pacientes[id].hospital = 0;
	}
}

class Coordinacion {
	class PacienteQueues{
		public ArrayDeque<SolicitarMsg> medicos;
		public ArrayDeque<SolicitarMsg> enfermeros;
		public ArrayDeque<SolicitarMsg> paramedicos;
		
		PacienteQueues(){
			this.medicos = new ArrayDeque<SolicitarMsg>();
			this.enfermeros = new ArrayDeque<SolicitarMsg>();
			this.paramedicos = new ArrayDeque<SolicitarMsg>();
		}
	}
	private List<PacienteQueues> queues;
	
	Coordinacion(int npacientes){
		this.queues = new ArrayList<PacienteQueues>();
		for (int i=0;i<npacientes;i++)
			this.queues.add(new PacienteQueues()); // una cola por paciente.
	}
	
	public void ponEnQueue(SolicitarMsg req){
		int id = req.getIdPaciente();
		String cargo = req.getEmpleado().getCargo();
		if(cargo.equals("doctor"))
			this.queues.get(id).medicos.addLast(req);
		if(cargo.equals("enfermero"))
			this.queues.get(id).enfermeros.addLast(req);
		if(cargo.equals("paramedico"))
			this.queues.get(id).paramedicos.addLast(req);
	}
	
	public SolicitarMsg getSiguiente(int id_paciente){
		//medicos
		if(!this.queues.get(id_paciente).medicos.isEmpty())
			return this.queues.get(id_paciente).medicos.pollFirst();
		//enfermeros
		if(!this.queues.get(id_paciente).enfermeros.isEmpty())
			return this.queues.get(id_paciente).enfermeros.pollFirst();
		//paramedicos
		if(!this.queues.get(id_paciente).paramedicos.isEmpty())
			return this.queues.get(id_paciente).paramedicos.pollFirst();
		return null;
	}
}

class AutorizaCliente implements Runnable{
	private static final Logger logger = Logger.getLogger(AutorizaCliente.class.getName());
	private ManagedChannel channel;
	private ReqCoordinacionGrpc.ReqCoordinacionStub asyncStub;
	private String dest;
	private SolicitudOk msg;
	
	AutorizaCliente(String direccion){
		this.dest = direccion;
	}
	
	@Override
	public void run() {
		this.daAcceso();
// 		try{
// 			
// 		} catch (IOException e){
// 			logger.warning("IO error");
// 		} catch (InterruptedException e){
// 			logger.warning("InterruptedException error");
// 		}
	}
	
	public void shutdown() throws InterruptedException {
		logger.info("Cerrando channel");
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}
	
	private void daAcceso(){
		String host = dest.split(":")[0];
		int port = Integer.parseInt(dest.split(":")[1]);
		channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		asyncStub = ReqCoordinacionGrpc.newStub(channel);
		logger.info("[permiteAcceso] Channel: " + dest);
			
		asyncStub.permiteAcceso(msg, new StreamObserver<Empty>() {
			@Override
			public void onNext(Empty resp) {
				// do nothing =)
			}
			@Override
			public void onError(Throwable t) {
				logger.info("Sin respuesta OK");
			}
			@Override
			public void onCompleted() {
				logger.info("Llego respuesta OK");
			}
		});
	}
}
