package tarea3;

import java.io.Writer;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.net.InetSocketAddress;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Expose;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.StatusRuntimeException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.protobuf.Empty;


public class Hospital {
	public static String PACIENTES_FILE = "pacientes.json";
	public static String REQUERIM_FILE = "requerimientos";
	public static String STAFF_FILE = "staff";
	public static String CONFIG_FILE = "configHospital";
	public static String hostname = "Unknown";
	public static final String MULTICAST_ADDRESS = "228.1.2.3";
	public static final int MULTICAST_PORT = 9876;
	public static final String COORDINADOR_ADDRESS = "228.3.3.3";
	public static final int COORDINADOR_PORT = 6789;
	public static final int BULLY = 1001;
	public static final int LOG = 1002;
	public static final int REQ = 1003;

	public static ConfigH config = null;
	public static ControlH ctrl;
	public static Staff staff;
	public static Paciente[] pacientes;

	Hospital(String hostname) throws IOException{
		System.out.println("Creando Hospital...");
		this.STAFF_FILE = "staff"+hostname+".json";
		this.CONFIG_FILE = "configHospital"+hostname+".json";
		this.REQUERIM_FILE = "requerimientos"+hostname+".json";
		BufferedReader bufferedReader = new BufferedReader(new FileReader(CONFIG_FILE));
		this.config = new Gson().fromJson(bufferedReader, ConfigH.class);
		bufferedReader.close();
		System.out.println("all done.\n\n");
	}

	public int port(int servicetype){
		if (this.config != null){
			if (servicetype == BULLY)
				return this.config.puerto_bully;
			if (servicetype == LOG)
				return this.config.puerto_logger;
			if (servicetype == REQ)
				return this.config.puerto_req;
		}
		return 0;
	}

	public int getId(){ // Id de este hospital.
		if (this.config != null)
			return this.config.id;
		return 0;
	}

	public List<String> vecindario(){
	// Direcciones IP de otros hospitales para eleccion bully.
		if (this.config != null)
			return this.config.vecindario();
		return null;
	}

	public void genCtrl(){
		this.ctrl = new ControlH(this.vecindario(), this.staff.doctores, this.getId());
	}

	class ConfigH {
		// Maneja informacion con las direcciones ip de los otros hospitales
		class HospData{
			@Expose
			public boolean locked;
			@Expose
			public int id;
			@Expose
			public int puerto_bully;
			@Expose
			public int puerto_logger;
			@Expose
			public int puerto_req;
			@Expose
			public String direccion;
		}
		@Expose
		public int id;
		@Expose
		public int puerto_bully;
		@Expose
		public int puerto_logger;
		@Expose
		public int puerto_req;
		@Expose
		public String direccion;
		@Expose
		public HospData[] hospitales = null;

		public int extcoordinador_id;
		public String extcoordinador_dir; //direccion ip:puerto del coordinador.

		/** Genera lista de vecinos (formato ip:puerto_bully) */
		public List<String> vecindario(){
			List<String> vecinos = new ArrayList<String>();
			for (HospData h : this.hospitales)
				if(h!=null && h.id != this.id)
					vecinos.add(h.direccion + ":" + h.puerto_bully);
			return vecinos;
		}

		/** Direccion ip:puerto del hospital y servicio especificado */
		public String getHospitalDir(int hospital_id, int port_type){
			for(HospData h : this.hospitales){
				if(h != null && h.id == hospital_id){
					if(port_type == BULLY)
						return h.direccion + ":" + h.puerto_bully;
					if(port_type == LOG)
						return h.direccion + ":" + h.puerto_logger;
					if(port_type == REQ)
						return h.direccion + ":" + h.puerto_req;
				}
			}
			return null;
		}

		/** Guarda direccion ip del coordinador si no es este hospital */
		public void guardaCoordinador(int id_hospital){
			this.extcoordinador_id = id_hospital;
			this.extcoordinador_dir = this.getHospitalDir(id_hospital, REQ);
		}

		/** Coordinador en este hospital (direccion) */
		public void guardaCoordinador(){
			this.extcoordinador_id = this.id;
			this.extcoordinador_dir = "127.0.0.1:"+this.puerto_req;
		}
	}

	class ControlH {
		// flags para eleccion bully
		private List<String> vecinos; // dirección hospitales vecinos (IP:Puerto)
		private ElectionMsg electionMsg;
		private BullyClient bClient;
		private RequerimientosServer reqServer;
		private ManejaRequerimientos mgmreq;

		private boolean en_eleccion = false; // Se esta escogiendo un coordinador. Me llego un mensaje o detecte fallo
		private boolean en_carrera = false; // true si no me a llegado mensaje de candidato mejor
		private boolean soy_coordinador = false;
		private boolean hay_coordinador = false; // coordinador en otro hospital.

		ControlH(List<String> v, List<Doctor> doctores, int idHospital){
			this.vecinos = v;
			// Genera el mejor candidato (segun mayor exp, estudios, id)
			int max_exp =  Collections.max(doctores, Comparator.comparing(d -> d.experiencia)).experiencia;
			int max_estudios = doctores.stream()
									.filter(d -> d.experiencia == max_exp)
									.max(Comparator.comparing(d -> d.estudios))
									.get().estudios;
			Doctor doc = doctores.stream()
							.filter(d -> d.experiencia == max_exp && d.estudios == max_estudios)
							.max(Comparator.comparing(d -> d.id))
							.get();
			System.out.println("[ctrl.candidatos] (op3.1) doc: " +doc.toString()+"\n"); // DEBUG
			this.electionMsg = ElectionMsg.newBuilder()
									.setIdHospital(idHospital)
									.setIdCandidato(doc.id)
									.setExperiencia(doc.experiencia)
									.setEstudios(doc.estudios)
									.build();
		}

		public void linkClient(BullyClient bClient){
			// instancia de cliente bully (envia msg de candidato y coordinador)
			this.bClient = bClient;
		}

		public void linkReqServer(RequerimientosServer reqServer){
			// instancia de servidor de requerimientos.
			this.reqServer = reqServer;
		}

		public void linkReqM(ManejaRequerimientos mgmreq){
			this.mgmreq = mgmreq;
		}

		public List<String> getVecindario(){
			return this.vecinos;
		}

		public ElectionMsg getElectionMsg(){
			// Para anunciar candidato. Msge tiene al mejor de este hospital
			return this.electionMsg;
		}

		// synchronized
		public void postulaEleccion(){
			this.en_eleccion = true;
			this.en_carrera = true;
			this.soy_coordinador = false;
			this.hay_coordinador = false;
			// client -> anunciaCandidato
			new Thread(bClient).start();
		}

		// synchronized
		public void abandonaEleccion(){
			this.en_eleccion = true;
			this.en_carrera = false;
			this.soy_coordinador = false;
			this.hay_coordinador = false;
		}

		// synchronized
		public  void iniciaCoordinacion(){
			this.en_eleccion = false;
			this.en_carrera = false;
			this.soy_coordinador = true;
			this.hay_coordinador = true;

			//Soy coordinador y creo un thread que escucha los cambios desde las maquinas
			new Thread(new LogThread( Hospital.MULTICAST_PORT, Hospital.COORDINADOR_ADDRESS, true)).start();

			// Gane eleccion, asumo coordinacion
			Doctor d = Hospital.staff.hazCoordinador(this.electionMsg.getIdCandidato());
			new Thread(new LogThread( Hospital.COORDINADOR_PORT, Hospital.MULTICAST_ADDRESS, true, "Doctor " + d.id + " (" + d.nombre + ")" + " elegido como coordinador")).start();
			this.reqServer.linkCoordinador(d);
			Hospital.config.guardaCoordinador(); // (direccion)
			this.mgmreq.destinoCoordinador(Hospital.config.extcoordinador_dir);
			// client -> anunciaCoordinador
			new Thread(bClient).start(); // Me anuncio como nuevo coordinador
		}

		// synchronized
		public void tomaCoordinadorExterno(CoordinadorMsg request){
			//No soy coordinador y creo un thread que escucha los cambios desde el coordinador
			Thread lt = new Thread(new LogThread( COORDINADOR_PORT, MULTICAST_ADDRESS, false));
			lt.start();

			this.en_eleccion = false;
			this.en_carrera = false;
			this.soy_coordinador = false;
			this.hay_coordinador = true;
			System.out.println("[coordinador externo] Hay que tomar coordinador en hospital: " + request.getIdHospital()+"\n");
			Hospital.config.guardaCoordinador(request.getIdHospital());
			this.mgmreq.destinoCoordinador(Hospital.config.extcoordinador_dir);
		}

		public boolean yaInicioEleccion(){
			return this.en_eleccion;
		}

		public boolean sigoEnCarrera(){
			return this.en_carrera;
		}

		public int hospitalID(){
			return this.electionMsg.getIdHospital();
		}

		public boolean soyMasFuerte(ElectionMsg req){
			// exp > estudios > id > id_hospital.
			if(this.electionMsg.getExperiencia() > req.getExperiencia())
				return true;
			else if(this.electionMsg.getExperiencia() < req.getExperiencia())
				return false;
			// igual exp
			if(this.electionMsg.getEstudios() > req.getEstudios())
				return true;
			else if(this.electionMsg.getEstudios() < req.getEstudios())
				return false;
			// igual exp & estudios
			if(this.electionMsg.getIdCandidato() > req.getIdCandidato())
				return true;
			else if(this.electionMsg.getIdCandidato() < req.getIdCandidato())
				return false;
			// igual exp & estudios & id
			if(this.electionMsg.getIdHospital() > req.getIdHospital())
				return true;
			return false;
		}
	}

	/**
	 * Main method.
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {

		Gson gson = new GsonBuilder()
							.setPrettyPrinting()
							.excludeFieldsWithoutExposeAnnotation()
							.create();

		BufferedReader bufferedReader = null;

		InetAddress addr;
		addr = InetAddress.getLocalHost();
		hostname = addr.getHostName();

		Hospital hospital = new Hospital(hostname);


		// Escucha actualizaciones
		new Thread(new LogThread( Hospital.COORDINADOR_PORT, Hospital.MULTICAST_ADDRESS, false)).start();

		// PACIENTES
		bufferedReader = new BufferedReader(new FileReader(PACIENTES_FILE));
		hospital.pacientes = new Gson().fromJson(bufferedReader, Paciente[].class);
		bufferedReader.close();

		// Revisa que ultimo elemento de pacientes no sea null.
		if(hospital.pacientes[hospital.pacientes.length - 1] == null) // VER
			hospital.pacientes = Arrays.copyOf(hospital.pacientes, hospital.pacientes.length-1);

		// STAFF
		bufferedReader = new BufferedReader(new FileReader(STAFF_FILE));
		hospital.staff = new Gson().fromJson(bufferedReader, Staff.class);
		bufferedReader.close();

		//////////////
		//
		//
		//////////////


		// Instancia ctrl
		hospital.genCtrl();

		// Servidor Bully
		BullyServer bServer = new BullyServer(hospital.port(BULLY), Hospital.ctrl);
		new Thread(bServer).start();

		// Cliente Bully (se lo pasa a ctrl.)
		BullyClient bClient = new BullyClient(Hospital.ctrl);
		Hospital.ctrl.linkClient(bClient);

		ManejaRequerimientos mreq = new ManejaRequerimientos(hospital.getId(), Hospital.ctrl);
		RequerimientosServer reqServer = new RequerimientosServer(hospital.port(REQ), Hospital.ctrl, mreq);
		new Thread(reqServer).start();

		Hospital.ctrl.linkReqM(mreq);
		Hospital.ctrl.linkReqServer(reqServer);

		// if(hostname.equals("dist9")){
		// if(hospital.getId() == 9){
		// 	TimeUnit.SECONDS.sleep(10);
		// 	Hospital.ctrl.postulaEleccion();
		// }

		// Espera 20 seg al principio para esperar primera eleccion.
		// Al parecer no es necesario. Terminan coordinandoce igual, pero con mas msges.
 		// TimeUnit.SECONDS.sleep(20);
		// if(hospital.getId() == 10){// DEBUG ELIMINAR ESTO!!! Todas las maquinas deben iniciar mreq.
		// 	new Thread(mreq).start();
		// }
		// Escucha actualizaciones
		// Thread lt = new Thread(new LogThread( COORDINADOR_PORT, MULTICAST_ADDRESS, false));
		// lt.start();
		// Thread asd = new Thread(new LogThread( COORDINADOR_PORT, MULTICAST_ADDRESS, true, Hospital.hostname + " - Ejecucion algoritmo del maton"));
		// asd.start();

		//Ejemplo funcional
		// Thread sdf = new Thread(new LogThread( COORDINADOR_PORT, MULTICAST_ADDRESS, false));
		// sdf.start();
		// Thread lt = new Thread(new LogThread( MULTICAST_PORT, COORDINADOR_ADDRESS, true));
		// lt.start();
		// Thread asd = new Thread(new LogThread( MULTICAST_PORT, Hospital.hostname, false, Hospital.hostname + " - Ejecucion algoritmo del maton"));
		// asd.start();

		// ???
		// Thread sdf = new Thread(new LogThread( COORDINADOR_PORT, MULTICAST_ADDRESS, false));
		// sdf.start();
		// Thread lt = new Thread(new LogThread( MULTICAST_PORT, COORDINADOR_ADDRESS, true));
		// lt.start();
		// Thread asd = new Thread(new LogThread( COORDINADOR_PORT, Hospital.hostname, true, Hospital.hostname + " - Ejecucion algoritmo del maton"));
		// asd.start();

		new Thread(mreq).start();
		// Envia mensaje de termino
		// new Thread(new LogThread( Hospital.MULTICAST_PORT, Hospital.hostname, false, Hospital.hostname + " - Fin del algoritmo del maton")).start();
	}
}

class Staff {
	@SerializedName(value = "Doctor", alternate = {"doctor", "Doctores", "doctores"})
	@Expose
	public List<Doctor> doctores;
	@SerializedName(value = "Paramedico", alternate = {"paramedico", "Paramedicos", "paramedicos"})
	@Expose
	public List<Paramedico> paramedicos;
	@SerializedName(value = "Enfermero", alternate = {"enfermero", "Enfermeros", "enfermeros"})
	@Expose
	public List<Enfermero> enfermeros;

	public Doctor hazCoordinador(int id){
		for (Doctor d : this.doctores){
			if(d!=null && d.id == id){
				System.out.println("[staff] Haciendo coordinador" + d.toString()+"\n");
				d.asumeCoordinacion(Hospital.pacientes, Hospital.config);
				System.out.println("[staff] Coordinador" + d.toString()+"\n");
				return d;
			}
		}
		System.out.println("[Staff.hazCoordinador] Error no debi haber llegado aca)\n");
		return null;
	}
}

// Cliente para enviar los mensajes de Candidato y Coordinador en eleccion Bully.
class BullyClient implements Runnable {
	private static final Logger logger = Logger.getLogger(BullyClient.class.getName());
	private List<ManagedChannel> channels;   // Para eleccion
	private List<ManagedChannel> channels2;  // Para anuncia candidato.
	private BullyGrpc.BullyStub asyncStub;
	private StreamObserver<OkMsg> resp;
	private static Hospital.ControlH ctrl;

	BullyClient(Hospital.ControlH ctrl){
		this.ctrl = ctrl;
		this.channels = new ArrayList<ManagedChannel>();
		this.channels2 = new ArrayList<ManagedChannel>();
	}

	@Override
	public void run() {
		if(this.ctrl.yaInicioEleccion()){ // Estoy en eleccion, debo mandar msg con candidato
			// // Envia log a todas las máquinas
			new Thread(new LogThread( Hospital.COORDINADOR_PORT, Hospital.MULTICAST_ADDRESS, true, Hospital.hostname + " - Ejecucion algoritmo del maton")).start();
			CountDownLatch finishLatch = this.anunciaCandidato();
			try {
				if (!finishLatch.await(6, TimeUnit.SECONDS)) {
					System.out.println("[BullyClient] (TIMEOUT) latch: \n>  " + finishLatch.toString()); // DEBUG
					logger.info("Timeout Sin respuestas OK, asumiendo coordinacion\n");
					if(this.ctrl.sigoEnCarrera())
						this.shutdown();
						this.ctrl.iniciaCoordinacion();
				} else{
					System.out.println("[BullyClient] (got respones) latch: \n>  " + finishLatch.toString()); // DEBUG
					logger.info("LLego respuesta Ok, abandonando eleccion\n");
					this.shutdown();
					this.ctrl.abandonaEleccion();
				}
			} catch (InterruptedException e){
				System.out.println("[BullyClient] Error en comunicación - Exception\n");
			}
			// Anuncia termino del algoritmo del maton
			new Thread(new LogThread( Hospital.MULTICAST_PORT, Hospital.hostname, false, Hospital.hostname + " - Fin del algoritmo del maton")).start();
		}
		else {
			logger.info("Anunciando coordinador\n");
			this.anunciaCoordinador();
			try{
				TimeUnit.SECONDS.sleep(1); // TODO en las maquinas puede que haya que subir.
				this.shutdown2(); // VER
			} catch (InterruptedException e){
				System.out.println("[BullyClient] InterruptedException shutdown\n");
			}
		}
	}

	public synchronized void shutdown() throws InterruptedException {
		System.out.println("[BullyClient] Cerrando channels\n");
		for (ManagedChannel channel : this.channels)
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		this.channels.clear();
	}

	public synchronized void shutdown2() throws InterruptedException {
		System.out.println("[BullyClient] Cerrando channels 2\n");
		for (ManagedChannel channel : this.channels2)
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		this.channels2.clear();
	}

	// Enviar mensaje con candidato propio (electionMsg), generado en la clase ControlH.
	// Hace un loop para enviar a todos los vecino. Gprc no soporta multicast.
	private CountDownLatch anunciaCandidato(){
		final CountDownLatch finishLatch = new CountDownLatch(1);//Basta que uno responda con OK.
		ElectionMsg electionMsg = this.ctrl.getElectionMsg();
		List<String> vecinos = this.ctrl.getVecindario();
		// Enviar candidato al resto de los hospitales para hacer eleccion.
		for (String dest: vecinos){
			String host = dest.split(":")[0];
			int port = Integer.parseInt(dest.split(":")[1]);
			channels2.add(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
			asyncStub = BullyGrpc.newStub(channels2.get(channels2.size()-1)); // TODO 1 stub x channel ??
			System.out.println("[anunciaCandidato] Channel: " + dest+"\n");

			asyncStub.iniciaEleccion(electionMsg, new StreamObserver<OkMsg>() {
				@Override
				public void onNext(OkMsg resp) {
					if(resp.getOk()==1){ //si ok = 0, se considera como si no hubiese llegado respuesta.
						System.out.println("Llego respuesta OK\n");
						finishLatch.countDown(); // TODO + 1 llamado?
					}
				}
				@Override
				public void onError(Throwable t) {
					System.out.println("Sin respuesta OK\n");
				}
				@Override
				public void onCompleted() {
					// do nothing =D
				}
			});
		}
		return finishLatch;
	}

	// No llegaron respuestas OK ni mensajes con mejores candidatos.
	// Se asume coordinacion y se envia mensaje por medio de esta funcion
	private void anunciaCoordinador(){
		List<String> vecinos = this.ctrl.getVecindario();
		CoordinadorMsg msg = CoordinadorMsg.newBuilder().setIdHospital(this.ctrl.hospitalID()).build();
		for (String dest: vecinos){
			String host = dest.split(":")[0];
			int port = Integer.parseInt(dest.split(":")[1]);
			channels.add(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
			asyncStub = BullyGrpc.newStub(channels.get(channels.size()-1)); // TODO 1 stup x channel ??
			System.out.println("[anunciaCoord] Channel: " + dest);

			asyncStub.anuncioCoordinacion(msg, new StreamObserver<OkMsg>() {
				@Override
				public void onNext(OkMsg resp) {
					// do nothing =)
				}
				@Override
				public void onError(Throwable t) {
					System.out.println("Sin respuesta OK\n");
				}
				@Override
				public void onCompleted() {
					System.out.println("Llego respuesta OK\n");
				}
			});
		}
	}
}

// Servidor Bully, escucha los mensajes relacionados a la eleccion.
class BullyServer implements Runnable {
	private static final Logger logger = Logger.getLogger(BullyServer.class.getName());
	private final int port;
	private final Server server;
	private static Hospital.ControlH ctrl;
	private static boolean shut = false;

	BullyServer(int port,Hospital.ControlH ctrl) {
		this.ctrl = ctrl;
		this.port = port;
		server = ServerBuilder.forPort(port)
					.addService(new BullyService())
					.build();
	}

	@Override
	public void run() {
		try{
			this.shut = false;
			this.startServer();
			this.blockUntilShutdown();
		} catch (IOException e){
			System.err.println("[bullyserver.run] IO error");
			System.out.println("[bullyserver.run] IO error");
		} catch (InterruptedException e){
			System.err.println("[bullyserver.run] InterruptedException error");
			System.out.println("[bullyserver.run] InterruptedException error");
		}
	}

	public void startServer() throws IOException {
		server.start();
		logger.info("Server started, listening on " + port+"\n");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				// Use stderr here since the logger may has been reset by its JVM shutdown hook.
				System.err.println("\n*** shutting down gRPC server since JVM is shutting down");
				BullyServer.this.stopServer(); // <-
				System.err.println("*** server shut down\n");
			}
		});
	}

	public void stopServer() {
		if (server != null && !this.shut) {
				this.shut = true;
				server.shutdown();
		}
	}

	protected void blockUntilShutdown() throws InterruptedException {
		if (server != null) {
			server.awaitTermination();
		}
	}


	// Servidor Bully eleccion
	private static class BullyService extends BullyGrpc.BullyImplBase {
		private static final Logger logger = Logger.getLogger(BullyService.class.getName());

		/** servicio eleccion */
		@Override
		public void iniciaEleccion(ElectionMsg request, StreamObserver<OkMsg> responseObserver) {
			logger.info("Recibi candidato\n");
			if(BullyServer.ctrl.soyMasFuerte(request)){ // si lo soy, envio OK y postuto a proc de eleccion.
				OkMsg msg = OkMsg.newBuilder().setOk(1).build();
				responseObserver.onNext(msg);
				if(!BullyServer.ctrl.yaInicioEleccion()){
					logger.info("Soy mejor, entro en carrera\n");
					BullyServer.ctrl.postulaEleccion();
				} else{
					logger.info("Soy mejor, pero ya estoy en carrera\n");
				}
			}else{// Ok = 0, simula que no se da respuesta. Grpc lanza error si intento cerrar asi no mas.
				OkMsg msg = OkMsg.newBuilder().setOk(0).build(); // Ok = 0
				responseObserver.onNext(msg);
				BullyServer.ctrl.abandonaEleccion();
			}
			responseObserver.onCompleted();
		}

		/** servicio anuncio coordinacion */
		@Override
		public void anuncioCoordinacion(CoordinadorMsg request, StreamObserver<OkMsg> responseObserver) {
			logger.info("Recibi coordinacion\n");
			BullyServer.ctrl.tomaCoordinadorExterno(request);
			OkMsg msg = OkMsg.newBuilder().setOk(1).build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
		}
	}
}

class RequerimientosServer implements Runnable {
	private static final Logger logger = Logger.getLogger(RequerimientosServer.class.getName());
	private final int port;
	private final Server server;
	private static Hospital.ControlH ctrl;
	private static Doctor coordinador;
	private static ManejaRequerimientos mgmreq;
	private static boolean shut = false;

	RequerimientosServer(int port, Hospital.ControlH ctrl, ManejaRequerimientos mgmreq) {
		this.ctrl = ctrl;
		this.port = port;
		this.mgmreq = mgmreq;
		server = ServerBuilder.forPort(port)
					.addService(new ReqCoordinacionService())
					.build();
	}

	@Override
	public void run() {
		try{
			this.startServer();
			this.blockUntilShutdown();
		} catch (IOException e){
			logger.warning("[ReqServer] IO error\n");
		} catch (InterruptedException e){
			logger.warning("[ReqServer] error\n");
		}
	}

	public void startServer() throws IOException {
		System.out.println("[reqServer.start] port: " + port+"\n"); // DEBUG
		server.start();
		logger.info("Server started, listening on " + port+"\n");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				// Use stderr here since the logger may has been reset by its JVM shutdown hook.
				System.err.println("\n*** shutting down gRPC server since JVM is shutting down");
				RequerimientosServer.this.stopServer(); // TODO VER
				System.err.println("*** server shut down\n");
			}
		});
	}

	public void stopServer() {
		if (server != null && !this.shut) {
				this.shut = true;
				server.shutdown();
		}
	}

	protected void blockUntilShutdown() throws InterruptedException {
		if (server != null) {
			server.awaitTermination();
		}
	}

	public void linkCoordinador(Doctor doc){
		this.coordinador = doc;
	}

	public void linkCtrl(Hospital.ControlH ctrl){
		this.ctrl = ctrl;
	}

	private static class ReqCoordinacionService extends ReqCoordinacionGrpc.ReqCoordinacionImplBase {
		private static final Logger logger = Logger.getLogger(ReqCoordinacionService.class.getName());

		/** servicio para Solicitar Ficha (acceso)*/
		@Override
		public void solicitarFicha(SolicitarMsg request, StreamObserver<SolicitudOk> responseObserver) {
			logger.info("Recibi solicitud acceso a paciente: "+request.getIdPaciente()+"(de hospital: "+request.getHospital()+")\n");
			// Soy coordinador y recibo una solicitud para acceder a una ficha.
			// Doctor coordinador checkea si la ficha esta disponible o no (status = 1, libre. status = 2, no).
			int status = RequerimientosServer.coordinador.solicitaFicha(request);
			SolicitudOk msg = SolicitudOk.newBuilder().setStatus(status).build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
		}

		/** servicio para recibir notificacion */
		@Override
		public void permiteAcceso(SolicitudOk request, StreamObserver<Empty> responseObserver) {
			logger.info("Recibiendo notificacion de acceso concedido (req: "+request.getIdRequerimiento()+")\n");
			// Coordinador me anuncia que requerimiento (request) es el siguiente para modificar al paciente.
			// Se marca requerimiento para mandarselo al coordinador y que este lo ejecute.
			RequerimientosServer.mgmreq.marcaParaRealizar(request);
			Empty msg = Empty.newBuilder().build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
		}

		/** servicio para modificar registro paciente*/
		@Override
		public void modificaPaciente(RequerimientoMsg request, StreamObserver<RequerimientoOk> responseObserver) {
			logger.info("Recibi mandato de requerimiento (req: "+request.getIdRequerimiento()+", hosp: "+request.getHospital()+")\n");
			// Ya se envio al hospital cliente que su requerimiento esta listo para ser procesado (ficha reservada para este cliente)
			// El cliente manda el requerimiento, esta funcion se lo pasa al coordinador para que realice las acciones del requerimiento.
			int status = RequerimientosServer.coordinador.modificaFicha(request);
			RequerimientoOk msg = RequerimientoOk.newBuilder()
											.setStatus(status)
											.setIdRequerimiento(request.getIdRequerimiento())
											.build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
		}
	}
}
