package tarea3;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.io.FileReader;
import java.io.BufferedReader;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.StatusRuntimeException;

import com.google.protobuf.Empty;

public class ManejaRequerimientos implements Runnable {
	private static final Logger logger = Logger.getLogger(ManejaRequerimientos.class.getName());
	private ManagedChannel channel;
	private ReqCoordinacionGrpc.ReqCoordinacionStub asyncStub;
	private String dest; // ip:puerto coordinador.
	
	public ArrayDeque<RequerimientoMsg> requerimientos;   // Lista inicial de requerimientos.
	public ArrayList<RequerimientoMsg> req_en_queue;      // Ficha ocupada; esperan aca hasta que el coordinador avise
	public ArrayDeque<RequerimientoMsg> req_por_realizar; // Listos para enviar (fichas reservadas para estos requerimientos)
	
	private boolean queueing_flag = false; // no se si es necesaria
	
	private static Hospital.ControlH ctrl;
	private boolean haycoordinador = false;
	private int req_counter; // Total de requerimientos
	
	ManejaRequerimientos(int hospital, Hospital.ControlH ctrl) throws FileNotFoundException, IOException, InterruptedException {
		this.ctrl = ctrl;
		this.haycoordinador = false;
		this.requerimientos = new ArrayDeque<RequerimientoMsg>();
		this.req_en_queue = new ArrayList<RequerimientoMsg>();
		this.req_por_realizar = new ArrayDeque<RequerimientoMsg>();
		
		// REQUERIMIENTOS
		logger.info("[] reqs FILE:" + Hospital.REQUERIM_FILE);
		BufferedReader bufferedReader = new BufferedReader(new FileReader(Hospital.REQUERIM_FILE));
		Requerimientos requs = new Gson().fromJson(bufferedReader, Requerimientos.class);
		bufferedReader.close();
		
		// Arma una lista con todos los requerimientos desglozados
		// Cada requerimiento se guarda como un mensaje listo para enviar coordinador (RequerimientoMsg).
		ArrayList<Requerimientos.Req> reqs = requs.requerimientos;
		RequerimientoMsg.Builder reqbd = RequerimientoMsg.newBuilder();
		Empl.Builder empl = Empl.newBuilder();
		this.req_counter = 0;
		for (int i = 0; i < reqs.size(); i++){
			if(reqs.get(i) != null){
				empl.setId(reqs.get(i).id);
				empl.setCargo(reqs.get(i).cargo);
				for (int j = 0; j < reqs.get(i).pacientes.size(); j++){
					if(reqs.get(i).pacientes.get(j) != null){
						reqbd.setIdRequerimiento(this.req_counter);
						reqbd.setHospital(hospital);
						reqbd.setIdPaciente(reqs.get(i).pacientes.get(j).idp);
						reqbd.setReqData(reqs.get(i).pacientes.get(j).requerimiento);
						reqbd.setEmpleado(empl.build());
						this.requerimientos.addLast(reqbd.build());
						this.req_counter++;
						reqbd.clear();
					}
				}
				empl.clear();
			}
		}
	}

	@Override
	public void run() {
		try{
			while(this.req_counter>0){
				TimeUnit.SECONDS.sleep(1);
				if(!this.haycoordinador){
					System.out.println("[Req.whileloop] No hay coordinador");
					this.ctrl.postulaEleccion();
					TimeUnit.SECONDS.sleep(20);
				}
				if(this.req_por_realizar.size()==0 && this.requerimientos.size() == 0){
					logger.info("Sin requerimientos que mandar, esperando 4 seg. (queue: "+req_counter+"-" + this.req_en_queue.size()+")");
					TimeUnit.SECONDS.sleep(4);
				}
				if(this.req_por_realizar.size()==0)
					this.iniciaRequerimientos();
				if(this.req_por_realizar.size()>0)
					this.realizaRequerimiento();
			}
			logger.info("Trabajo completado, se realizaron todos los requerimientos");
		}catch (InterruptedException e){
		}
	}

	public void destinoCoordinador(String dest){
		this.dest = dest; // ip:puerto coordinador.
		this.haycoordinador = true;
	}

	// synchronized
	public void iniciaRequerimientos(){
		// Le pide al coordinador acceso a la ficha. (Respuesta del coordinador se maneja en funcion: handleResp)
		// si no obtiene respuesta, asume coordinador caido e inicia eleccion.
		RequerimientoMsg reqm = this.requerimientos.peekFirst();
		SolicitarMsg.Builder msg = SolicitarMsg.newBuilder();
		msg.setIdRequerimiento(reqm.getIdRequerimiento());
		msg.setIdPaciente(reqm.getIdPaciente());
		msg.setHospital(reqm.getHospital());
		msg.setEmpleado(reqm.getEmpleado());
		CountDownLatch finishLatch = this.pideFicha(msg.build());
		try{
			if (!finishLatch.await(20, TimeUnit.SECONDS)){
				logger.info("No hay respuesta por parte del coordinador. Iniciando eleccion");
				this.haycoordinador = false;
			}
		} catch(InterruptedException e){
		}
	}

	// synchronized
	public void realizaRequerimiento(){
		// Manda requerimiento ya autorizado por el coordinador. (Ficha/paciente esta reservada para este requerimiento)
		RequerimientoMsg msg = this.req_por_realizar.peekFirst();
		CountDownLatch finishLatch = this.mandaRequerimiento(msg);
		try{
			if (!finishLatch.await(20, TimeUnit.SECONDS)){
				logger.info("No hay respuesta por parte del coordinador. Iniciando eleccion");
				this.haycoordinador = false;
			}
		} catch(InterruptedException e){
		}
	}

	// synchronized
	public void terminaRequerimiento(RequerimientoOk resp){
		// requerimiento (1ro en lista: req_por_realizar) acaba de ser procesado por coordinador.
		// Se hicieron los cambios si eran pertinentes.
		// status = 2 implica un enfermero o paramedico intentando cambiar algo que no le corresponde.
		if (resp.getStatus() == 1){
			logger.info("Requerimiento id: " + resp.getIdRequerimiento() + " COMPLETADO.");
		} else if(resp.getStatus() == 2){
			logger.info("Requerimiento id: " + resp.getIdRequerimiento() + " ACCESO DENEGADO.");
		} else{
			logger.warning("*** codigo desconocido - status: " + resp.getStatus());
		}
		// con status 1 o 2 se elimina igual el req (ya fue procesado).
		this.req_por_realizar.removeFirst();
		this.req_counter--;
	}

	// synchronized
	public void marcaParaRealizar(SolicitudOk req){
		// Servidor anuncia que se desocupo la ficha y el requerimiento req es el siguiente.
		// Se pasa requerimento de lista de espera (req_en_queue) a lista para ejecutar (req_por_realizar).
// 		if(this.queueing_flag = true)
// 			wait();
		this.queueing_flag = true;
		int i = 0;
		for(i=0; i<this.req_en_queue.size(); i++)
			if(this.req_en_queue.get(i).getIdRequerimiento() == req.getIdRequerimiento())
				break;
		this.req_por_realizar.addLast(this.req_en_queue.get(i));
		this.req_en_queue.remove(i);
		this.queueing_flag = false;
	}

	// synchronized
	public void marcaParaRealizar(){
		// Ficha estaba disponible, requerimiento pasa directamente a lista para ejecutar.
		this.req_por_realizar.addLast(this.requerimientos.pollFirst());
	}

	// synchronized
	public void mandaQueue(){
		// Ficha ocupada, manda requerimiento a lista de espera.
		this.req_en_queue.add(this.requerimientos.pollFirst());
	}

	public void handleResp(SolicitudOk resp){
		// Respuesta del servidor a la solicitud de ficha.
		// status = 1, ficha desocupada; status = 2, ficha ocupada y hay que esperar.
		if(resp.getStatus()==1){
			this.marcaParaRealizar();
		}
		if(resp.getStatus()==2){
			this.mandaQueue();
		}
	}

	public void shutdown() throws InterruptedException {
		logger.info("Cerrando channel");
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	private CountDownLatch pideFicha(SolicitarMsg msg){
		// Manda mensaje al servidor del coordinador, pidiendo acceso a la ficha.
		final CountDownLatch finishLatch = new CountDownLatch(1);
		String host = dest.split(":")[0];
		int port = Integer.parseInt(dest.split(":")[1]);
		channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		asyncStub = ReqCoordinacionGrpc.newStub(channel);
		logger.info("[pideFicha] Channel: " + dest);
		asyncStub.solicitarFicha(msg, new StreamObserver<SolicitudOk>() {
			@Override
			public void onNext(SolicitudOk resp) {
				System.out.println("[pideFicha] onNext");
				handleResp(resp);
			}
			@Override
			public void onError(Throwable t) {
				System.out.println("[pideFicha] Error");
			}
			@Override
			public void onCompleted() {
				logger.info("End pideFicha");
				System.out.println("[pideFicha] onCompleted");
				finishLatch.countDown();
			}
		});
		return finishLatch;
	}

	private CountDownLatch mandaRequerimiento(RequerimientoMsg msg){
		// Manda al coordinador requerimiento listo para procesar.
		// Aqui, la ficha ya esta apartada para este requerimiento.
		final CountDownLatch finishLatch = new CountDownLatch(1);
		String host = dest.split(":")[0];
		int port = Integer.parseInt(dest.split(":")[1]);
		channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		asyncStub = ReqCoordinacionGrpc.newStub(channel);
		logger.info("[pideFicha] Channel: " + dest);
		asyncStub.modificaPaciente(msg, new StreamObserver<RequerimientoOk>() {
			@Override
			public void onNext(RequerimientoOk resp) {
				terminaRequerimiento(resp);
			}
			@Override
			public void onError(Throwable t) {
				System.out.println("[mandaRequerimiento] Coordinador no contesta");
			}
			@Override
			public void onCompleted() {
				logger.info("End.");
				finishLatch.countDown();
			}
		});
		return finishLatch;
	}
}
