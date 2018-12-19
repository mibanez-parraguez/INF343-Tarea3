package tarea3;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

import java.lang.Thread;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.UnknownHostException;

class LogThread implements Runnable {
  private Thread t;
	public static final String MULTICAST_ADDRESS = "228.1.2.3";
	public static final int MULTICAST_PORT = 9876;
	public static final String COORDINADOR_ADDRESS = "228.3.3.3";
	public static final int COORDINADOR_PORT = 6789;
  private int mcPort;
  private String mcIPStr;
  private boolean soyCoordinador;  // si soy coordinador o no
  private boolean enviar;          // si el thread es para enviar actualizacion
  private String mensaje;
  private boolean receptorCoordinador; // si soy coordinador receptor
  private Logger logger = Logger.getLogger("log");
  private FileHandler fh;

  // Constructor para LogThread que envia actualizaciones
  LogThread(int puerto, String direccion, boolean soyCoordinador, String mensaje) {
    this.mcPort = puerto;
    this.mcIPStr = direccion;
    this.soyCoordinador = soyCoordinador;
    this.enviar = true;
    this.mensaje = mensaje;
    this.receptorCoordinador = false;
  }
  // Constructor para LogThread que espera actualizaciones
  LogThread(int puerto, String direccion, boolean soyCoordinador) {
    this.mcPort = puerto;
    this.mcIPStr = direccion;
    this.soyCoordinador = soyCoordinador;
    this.enviar = false;
    this.receptorCoordinador = false;
  }
  // Constructor para LogThread que espera actualizaciones si es coordinador
  // private LogThread(int puerto, String direccion, boolean soyCoordinador, boolean receptorCoordinador) {
  //   this.mcPort = puerto;
  //   this.mcIPStr = direccion;
  //   this.soyCoordinador = soyCoordinador;
  //   this.enviar = false;
  //   this.receptorCoordinador = receptorCoordinador;
  //   System.out.println("[Log] Iniciando listener actualizacions, soy coordinador");
  // }

  public void run() {
    // El thread es para enviar actualizaciones y es temporal, se detiene al terminar de enviar
    if (this.enviar) {
      // Si soy el coordinador entonces envio el mensaje a todas las maquinas por multicast udp
      if (soyCoordinador) {
        EnviarActualizacionAlSistema(mensaje);
      }
      // Si no soy coordinador entonces envio el mensaje al coordinador
      else {
        EnviarActualizacionAlCoordinador(mensaje);
      }
    }
    // El thread es para recibir actualizaciones y es permanente, siempre espera recibir
    else {
      // if (!receptorCoordinador) {
      //   System.out.println("[Log] RecibirActualizacionDesdeCoordinador");
      //   RecibirActualizacionDesdeCoordinador();
      // }
      // // Si es coordinador entonces abre otro socket para recibir actualizaciones desde las maquinas
      // if (soyCoordinador) {
      //   Thread coordinador_lt = new Thread(new LogThread(this.mcPort, this.mcIPStr, false, true));
      //   coordinador_lt.start();
      // }
      // //ignorar nombre de variables, este if solo se cumple si el thread original es coordinador
      // //coordinador recibe actualizaciones desde las maquinas y las propaga
      // if (!soyCoordinador && receptorCoordinador) {
      //   System.out.println("[Log] RecibirActualizacionDesdeSistema");
      //   RecibirActualizacionDesdeSistema();
      // }

      // Si no soy coordinador entonces recibo actualizaciones desde multicast udp
      if (!soyCoordinador) {
        RecibirActualizacionDesdeCoordinador();
      }
      // Si soy coordinador entonces recibo actualizaciones
      else {
        RecibirActualizacionDesdeSistema();
      }
    }
  }


  // Enviar actualizacion de log al coordinador
  public void EnviarActualizacionAlCoordinador(String entrada) {
    try {
      DatagramSocket clientSocket = new DatagramSocket();
      InetAddress IPAddress = InetAddress.getByName(mcIPStr);
      byte[] sendData = new byte[1024];
      sendData = entrada.getBytes();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, mcPort);
      clientSocket.send(sendPacket);
      System.out.println("[Log] Mensaje enviado a coordinador: " + entrada);
      System.out.println("[Log] Mensaje enviado a coordinador: " + IPAddress + " port: " + mcPort);
      clientSocket.close();
    } catch (UnknownHostException e) {
      System.out.println("[Log] Error al actualizar Log, host desconocido: ");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("[Log] Error al actualizar Log, IO exception: ");
      e.printStackTrace();
    }
  }
  // Coordinador envia actualizacion de log a todos las maquinas
  public void EnviarActualizacionAlSistema(String entrada) {
    try {
      DatagramSocket udpSocket = new DatagramSocket();
      InetAddress mcIPAddress = InetAddress.getByName(this.mcIPStr);
      byte[] msg = entrada.getBytes();
      DatagramPacket packet = new DatagramPacket(msg, msg.length);
      packet.setAddress(mcIPAddress);
      packet.setPort(mcPort);
      udpSocket.send(packet);
      // System.out.println("[Log] Mensaje enviado desde coordinador: " + entrada + "; address: " + packet.getAddress() + "; port: " + packet.getPort());
      System.out.println("[Log] Mensaje enviado desde coordinador: " + entrada);
      udpSocket.close();
    } catch (UnknownHostException e) {
      System.out.println("[Log] Error al actualizar Log, host desconocido: ");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("[Log] Error al actualizar Log, IO exception: ");
      e.printStackTrace();
    }
  }
  // Maquinas reciben actualizacion de log
  public void RecibirActualizacionDesdeCoordinador() {
    try {
      MulticastSocket mcSocket = null;
      InetAddress mcIPAddress = null;
      mcIPAddress = InetAddress.getByName(this.mcIPStr);
      mcSocket = new MulticastSocket(this.mcPort);
      // System.out.println("[Log] Recibiendo actualizaciones en" + mcSocket.getLocalSocketAddress());
      mcSocket.joinGroup(mcIPAddress);
      DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
      while (true) {
        System.out.println("[Log] Esperando acutalizaciones...");
        mcSocket.receive(packet);
        String msg = new String(packet.getData(), packet.getOffset(), packet.getLength());
        if (msg.equals("exit")) {
          break;
        }
        System.out.println("[Log] Actualizacion recibida desde coordinador: " + msg);
        //Al recibir el mensaje se actualizan todas las maquinas, incluyendo coordinador
        ActualizarLog(msg);
      }
      mcSocket.leaveGroup(mcIPAddress);
      mcSocket.close();
    } catch (UnknownHostException e) {
      System.out.println("[Log] Error al actualizar Log, host desconocido: ");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("[Log] Error al actualizar Log, IO exception: ");
      e.printStackTrace();
    }
  }
  // Coordinador reciben actualizacion de log
  public void RecibirActualizacionDesdeSistema() {
    try {
      DatagramSocket serverSocket = new DatagramSocket(mcPort);
      byte[] receiveData = new byte[1024];
      while(true)
      {
        System.out.println("[Log] Coordinador esperando acutalizaciones...");
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        serverSocket.receive(receivePacket);
        String msg = new String( receivePacket.getData());
        System.out.println("[Log] Actualizacion recibida en coordinador:" + msg);
        // Enviar mensaje al grupo multicast
        LogThread lt_enviar_multi = new LogThread(COORDINADOR_PORT, MULTICAST_ADDRESS, true, msg);
    		lt_enviar_multi.start();
        System.out.println("[Log] Coordinador enviando actualizacion a maquinas:" + msg);
      }
    } catch (UnknownHostException e) {
      System.out.println("[Log] Error al actualizar Log, host desconocido: ");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("[Log] Error al actualizar Log, IO exception: ");
      e.printStackTrace();
    }
  }

  // Actualiza el log localmente, solo debe hacerse desde grupo multicast para asegurar que todas las maquinas tienen lo mismo
  public void ActualizarLog(String mensaje) {
    try {
      String path = System.getProperty("user.dir");
      fh = new FileHandler(path+"/log.log", true);
      logger.addHandler(fh);
      SimpleFormatter formatter = new SimpleFormatter();
      fh.setFormatter(formatter);
      logger.info(mensaje);
      fh.close();

    } catch (SecurityException e) {
      System.out.println("[Log] Error al actualizar Log, host desconocido: ");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("[Log] Error al actualizar Log, host desconocido: ");
      e.printStackTrace();
    }
  }

  public void start () {
    if (t == null) {
      t = new Thread (this);
      t.start ();
    }
  }
}
