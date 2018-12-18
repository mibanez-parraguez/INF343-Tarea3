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

public class ManejaRequerimientos {
// 	class MiReq{
// 		public int id_req;
// 		public int id_paciente;
// 		public int id_staff;
// 		public String cargo;
// 		
// 	
// 	}

	public ArrayDeque<RequerimientoMsg> requerimientos;
	public ArrayList<RequerimientoMsg> req_en_queue;
}
