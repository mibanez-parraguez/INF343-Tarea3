package tarea3;

import java.io.Writer;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.util.List;
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


public class Hospital {
	public static String PACIENTES_FILE = "pacientes.json";
	public static String REQUERIM_FILE = "requerimientos.json";
	public static String STAFF_FILE = "staff";
	public static String CONFIG_FILE = "configHospital";
	public static String hostname = "Unknown";
	public static final int BULLY = 1001;
	public static final int LOG = 1002;

	private ConfigH config = null;
	public static ControlH ctrl;
	public static Staff staff;

	Hospital(String hostname) throws IOException{
		System.out.println("Creando Hospital...");
		this.STAFF_FILE = "staff"+hostname+".json";
		this.CONFIG_FILE = "configHospital"+hostname+".json";
		BufferedReader bufferedReader = new BufferedReader(new FileReader(CONFIG_FILE));
		this.config = new Gson().fromJson(bufferedReader, ConfigH.class);
		bufferedReader.close();
		System.out.println("all done.\n\n");
	}

	public String getGreeting() {
		return "Hello world.";
	}

	public int port(int servicetype){
		if (this.config != null){
			if (servicetype == BULLY)
				return this.config.puerto_bully;
		}
		return 0;
	}

	public int getId(){
		if (this.config != null)
			return this.config.id;
		return 0;
	}

	public List<String> vecindario(){
		if (this.config != null)
			return this.config.vecindario();
		return null;
	}

	public void genCtrl(){
		this.ctrl = new ControlH(this.vecindario(), this.staff.doctores, this.getId());
	}

	class ConfigH {
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
			public String direccion;
		}
		@Expose
		public int id;
		@Expose
		public int puerto_bully;
		@Expose
		public int puerto_logger;
		@Expose
		public String direccion;
		@Expose
		public HospData[] hospitales = null;

		public int extcoordinador_id;

		/** Genera lista de vecinos (formato ip:puerto_bully) */
		public List<String> vecindario(){
			List<String> vecinos = new ArrayList<String>();
			for (HospData h : this.hospitales)
				if(h!=null && h.id != this.id)
					vecinos.add(h.direccion + ":" + h.puerto_bully);
			return vecinos;
		}

		public void guardaCoordinadorExterno(int id_hospital){
			this.extcoordinador_id = id_hospital;
		}
	}

	class ControlH {
		//private static final Logger logger = Logger.getLogger(ControlH.class.getName()); // modifier 'static' is only allowed in constant variable declarations
		private List<String> vecinos; // dirección hospitales vecinos (IP:Puerto)
		private ElectionMsg electionMsg;
		private BullyClient bClient;

		private boolean en_eleccion = false;
		private boolean en_carrera = false;
		private boolean soy_coordinador = false;
		private boolean hay_coordinador = false;

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
			System.out.println("[ctrl.candidatos] (op3.1) doc: " +doc.toString()); // DEBUG
			this.electionMsg = ElectionMsg.newBuilder()
									.setIdHospital(idHospital)
									.setIdCandidato(doc.id)
									.setExperiencia(doc.experiencia)
									.setEstudios(doc.estudios)
									.build();
		}

		public void linkClient(BullyClient bClient){
			this.bClient = bClient;
		}

		public List<String> getVecindario(){
			return this.vecinos;
		}

		public ElectionMsg getElectionMsg(){
			return this.electionMsg;
		}

		// TODO necesita sync ?
		public synchronized void postulaEleccion(){
			this.en_eleccion = true;
			this.en_carrera = true;
			this.soy_coordinador = false;
			this.hay_coordinador = false;
			// client -> anunciaCandidato
			new Thread(bClient).start();
		}

		// TODO necesita sync ?
		public synchronized void abandonaEleccion(){
			this.en_eleccion = true;
			this.en_carrera = false;
			this.soy_coordinador = false;
			this.hay_coordinador = false;
		}

		public synchronized void iniciaCoordinacion(){
			this.en_eleccion = false;
			this.en_carrera = false;
			this.soy_coordinador = true;
			this.hay_coordinador = true;

			Hospital.staff.hazCoordinador(this.electionMsg.getIdCandidato());
			// client -> anunciaCoordinador
			new Thread(bClient).start();
		}

		public synchronized void tomaCoordinadorExterno(CoordinadorMsg request){
			this.en_eleccion = false;
			this.en_carrera = false;
			this.soy_coordinador = false;
			this.hay_coordinador = true;
			System.out.println("[coordinador externo] Hay que tomar coordinador en hospital: " + request.getIdHospital());
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

		System.out.println("Hello!!\n");


		bufferedReader = new BufferedReader(new FileReader(PACIENTES_FILE));
		Paciente[] pacientes = new Gson().fromJson(bufferedReader, Paciente[].class);
		bufferedReader.close();

		System.out.println("[1]" + pacientes[0].datos_personales[0].nombre);
		System.out.println("[2]" + pacientes[0].enfermedades.get(1));

		System.out.println("[3.1]" + pacientes[0].procedimientos[0].asignados.get(0));
		System.out.println("[3.3]" + pacientes[0].procedimientos[1].completados.get(0));

		System.out.println("[4.1]" + pacientes[0].examenes[1].no_realizados.get(0));
		System.out.println("[4.2]" + pacientes[0].examenes[0].realizados.get(0));

		System.out.println("[5.1]" + pacientes[0].medicamentos[0].recetados.get(0));
		System.out.println("[5.2]" + pacientes[0].medicamentos[1].suministrados.get(0));


		bufferedReader = new BufferedReader(new FileReader(STAFF_FILE));
		hospital.staff = new Gson().fromJson(bufferedReader, Staff.class);
		bufferedReader.close();

		System.out.println("Staff");
		System.out.println("[1.1] " + hospital.staff.doctores.get(0).toString());
		System.out.println("[1.2] " + hospital.staff.doctores.get(1).toString());
		System.out.println("[1.3] " + hospital.staff.doctores.get(2).toString());
		System.out.println("[2.1] " + hospital.staff.paramedicos.get(0).toString());
		System.out.println("[3.1] " + hospital.staff.enfermeros.get(0).toString());

		//hospital.ctrl = new Hospital.ControlH(hospital.vecindario(), hospital.staff.doctores, hospital.port(BULLY));
 		hospital.genCtrl();

 		BullyServer bServer = new BullyServer(hospital.port(BULLY), Hospital.ctrl);
 		new Thread(bServer).start();
		BullyClient bClient = new BullyClient(Hospital.ctrl);
		Hospital.ctrl.linkClient(bClient);

		System.out.println("Haciendo algo...");

		if(hospital.getId() == 1){
			TimeUnit.SECONDS.sleep(36);
			Hospital.ctrl.postulaEleccion();
		}
		//bServer.stopServer();
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

	public void hazCoordinador(int id){
		for (Doctor d : this.doctores){
			if(d.id == id){
				System.out.println("[staff] Haciendo coordinador" + d.toString());
				d.asumeCoordinacion();
				System.out.println("[staff] Coordinador" + d.toString());
			}
		}
	}
}



class BullyClient implements Runnable {
	private static final Logger logger = Logger.getLogger(BullyClient.class.getName());
	private List<ManagedChannel> channels;
	private BullyGrpc.BullyStub asyncStub;
	private StreamObserver<OkMsg> resp;
	private static Hospital.ControlH ctrl;

	BullyClient(Hospital.ControlH ctrl){
		this.ctrl = ctrl;
		this.channels = new ArrayList<ManagedChannel>();
		System.out.println("[BClient] this.ctrl " + Hospital.ctrl);
	}

	@Override
	public void run() {
		if(this.ctrl.yaInicioEleccion()){
			CountDownLatch finishLatch = this.anunciaCandidato();
			try {
				if (!finishLatch.await(30, TimeUnit.SECONDS)) {
					logger.info("Timeout Sin respuestas OK, asumiendo coordinacion");
					if(this.ctrl.sigoEnCarrera())
						this.ctrl.iniciaCoordinacion();
// 					this.shutdown();
				} else{
					logger.info("LLego respuesta Ok, abandonando eleccion");
					this.ctrl.abandonaEleccion();
// 					this.shutdown();
				}
				System.out.println("[bClient] fin block try");
			} catch (InterruptedException e){
				System.out.println("Error en comunicación - Exception");
			}
			System.out.println("[bClient] LLegue al final 111");
		}
		else {
			logger.info("Anunciando coordinador");
			this.anunciaCoordinador();
			try{
				TimeUnit.SECONDS.sleep(4);
// 				this.shutdown();
			} catch (InterruptedException e){
				System.out.println("InterruptedException shutdown");
			}
		}
	}

	public synchronized void shutdown() throws InterruptedException {
		System.out.println("Cerrando channels");
		for (ManagedChannel channel : this.channels)
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		this.channels.clear();
	}

	private CountDownLatch anunciaCandidato(){
		final CountDownLatch finishLatch = new CountDownLatch(1);//Basta que uno responda con OK.
		ElectionMsg electionMsg = this.ctrl.getElectionMsg();
		List<String> vecinos = this.ctrl.getVecindario();
		List<ManagedChannel> channels2 = new ArrayList<ManagedChannel>();
		// Enviar candidato al resto de los hospitales para hacer eleccion.
		for (String dest: vecinos){
			String host = dest.split(":")[0];
			int port = Integer.parseInt(dest.split(":")[1]);
			channels2.add(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
			asyncStub = BullyGrpc.newStub(channels2.get(channels2.size()-1)); // TODO 1 stup x channel ??
			System.out.println("[eleccion] Channel: " + dest);

			asyncStub.iniciaEleccion(electionMsg, new StreamObserver<OkMsg>() {
				@Override
				public void onNext(OkMsg resp) {
					if(resp.getOk()==1){ //si ok = 0, se considera como si no hubiese llegado respuesta.
						logger.info("Llego respuesta OK");
						finishLatch.countDown(); // TODO + 1 llamado? 
					}
				}
				@Override
				public void onError(Throwable t) {
					logger.info("Sin respuesta OK");
				}
				@Override
				public void onCompleted() {
					// do nothing =D
				}
			});
		}
		return finishLatch;
	}

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
					logger.info("Sin respuesta OK");
				}
				@Override
				public void onCompleted() {
					logger.info("Llego respuesta OK");
				}
			});
		}
	}
}


class BullyServer implements Runnable {
	private static final Logger logger = Logger.getLogger(BullyServer.class.getName());
	private final int port;
	private final Server server;
	private static Hospital.ControlH ctrl;

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
		logger.info("Server started, listening on " + port);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				// Use stderr here since the logger may has been reset by its JVM shutdown hook.
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				//BullyServer.this.stopServer(); //TODO
				System.err.println("*** server shut down");
			}
		});
	}

	public void stopServer() {
		if (server != null) {
//			if (!server.isShutdown()){
				server.shutdown();
//			}
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
			logger.info("Recibi candidato");
			if(BullyServer.ctrl.soyMasFuerte(request)){ // si lo soy, envio OK y ctrluto proc de eleccion.
				OkMsg msg = OkMsg.newBuilder().setOk(1).build();
				responseObserver.onNext(msg);
				if(!BullyServer.ctrl.yaInicioEleccion()){
					logger.info("Soy mejor, entro en carrera");
					BullyServer.ctrl.postulaEleccion();
				} else{
					logger.info("Soy mejor, pero ya estoy en carrera");
				}
			}else{
				OkMsg msg = OkMsg.newBuilder().setOk(0).build(); // Ok = 0, simula que no respondo. 
				responseObserver.onNext(msg);
				BullyServer.ctrl.abandonaEleccion();
			}
			responseObserver.onCompleted();
		}

		/** servicio anuncio coordinacion */
		@Override
		public void anuncioCoordinacion(CoordinadorMsg request, StreamObserver<OkMsg> responseObserver) {
			logger.info("Recibi coordinacion");
			BullyServer.ctrl.tomaCoordinadorExterno(request);
			OkMsg msg = OkMsg.newBuilder().setOk(1).build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
		}
	}
}
