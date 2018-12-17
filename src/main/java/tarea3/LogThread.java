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
  private static int mcPort;
  private static String mcIPStr;
  private static int idCoordinador;  //id hospital coordinador
  private static int idLocal;        //id hospital de esta maquina
  private static boolean soyCoordinador;
  private static boolean enviar;     //si el thread es para enviar actualizacion
  private static String mensaje;
  private static boolean receptorCoordinador; // si soy coordinador receptor
  private static Logger logger = Logger.getLogger("log");
  private static FileHandler fh;

  // Constructor para LogThread que envia actualizaciones
  LogThread(int puerto, String direccion, int coordinador, int local, boolean enviar, String mensaje) {
    this.mcPort = puerto;
    this.mcIPStr = direccion;
    this.idCoordinador = coordinador;
    this.idLocal = local;
    this.soyCoordinador = idLocal == idCoordinador;
    this.enviar = enviar;
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
  private LogThread(int puerto, String direccion, boolean soyCoordinador, boolean receptorCoordinador) {
    this.mcPort = puerto;
    this.mcIPStr = direccion;
    this.soyCoordinador = soyCoordinador;
    this.enviar = false;
    this.receptorCoordinador = receptorCoordinador;
  }

  public void run() {
    // El thread es para enviar actualizaciones y es temporal, se detiene al terminar de enviar
    if (this.enviar) {
      // Si soy el coordinador entonces envio el mensaje a todas las maquinas
      if (soyCoordinador) {
        EnviarActualizacionSistema(mensaje);
      }
      // Si no soy coordinador entonces envio el mensaje al coordinador
      else {
        EnviarActualizacionCoordinador(mensaje);
      }
    }
    // El thread es para recibir actualizaciones y es permanente, siempre espera recibir
    else {
      if (!receptorCoordinador) {
        RecibirActualizacionSistema();
      }
      if (soyCoordinador) {
        LogThread lt = new LogThread(this.mcPort, this.mcIPStr, false, true);
        new Thread(lt).start();
      }
    }
  }


  // Enviar actualizacion de log al coordinador
  public void EnviarActualizacionCoordinador(String entrada) {
    try {
      DatagramSocket udpSocket = new DatagramSocket();
      InetAddress mcIPAddress = InetAddress.getByName(mcIPStr);
      byte[] msg = entrada.getBytes();
      DatagramPacket packet = new DatagramPacket(msg, msg.length);
      packet.setAddress(mcIPAddress);
      packet.setPort(mcPort);
      udpSocket.send(packet);
      System.out.println("[Log] Mensaje enviado a coordinador");
      udpSocket.close();
    } catch (UnknownHostException e) {
      System.out.println("[Log] Error al actualizar Log, host desconocido: ");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("[Log] Error al actualizar Log, IO exception: ");
      e.printStackTrace();
    }
  }
  // Coordinador envia actualizacion de log a todos las maquinas
  public void EnviarActualizacionSistema(String entrada) {
    try {
      DatagramSocket udpSocket = new DatagramSocket();
      InetAddress mcIPAddress = InetAddress.getByName(mcIPStr);
      byte[] msg = entrada.getBytes();
      DatagramPacket packet = new DatagramPacket(msg, msg.length);
      packet.setAddress(mcIPAddress);
      packet.setPort(mcPort);
      udpSocket.send(packet);
      System.out.println("[Log] Mensaje enviado desde coordinador");
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
  public void RecibirActualizacionSistema() {
    try {
      MulticastSocket mcSocket = null;
      InetAddress mcIPAddress = null;
      mcIPAddress = InetAddress.getByName(mcIPStr);
      mcSocket = new MulticastSocket(mcPort);
      System.out.println("Recibiendo actualizaciones en:" + mcSocket.getLocalSocketAddress());
      mcSocket.joinGroup(mcIPAddress);

      DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
      while (true) {
        System.out.println("Esperando acutalizaciones...");
        mcSocket.receive(packet);
        String msg = new String(packet.getData(), packet.getOffset(), packet.getLength());
        if (msg.equals("exit")) {
          break;
        }
        System.out.println("[Log] Actualizacion recibida:" + msg);
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

  // Actualiza el log localmente
  public void ActualizarLog(String mensaje) {
    try {
      String path = System.getProperty("user.dir");
      fh = new FileHandler(path+"/log.log");
      logger.addHandler(fh);
      SimpleFormatter formatter = new SimpleFormatter();
      fh.setFormatter(formatter);
      logger.info("My first log");
      logger.info(mensaje);

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
