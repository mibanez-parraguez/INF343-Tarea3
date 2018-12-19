package tarea3;

import java.util.ArrayList;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Requerimientos {
	
	class PacienteReq{
		@SerializedName("ip")
		@Expose
		public int id;
		@SerializedName("requerimiento")
		@Expose
		public String requerimiento;
	}

	class Req{
		@SerializedName("id")
		@Expose
		public int id;
		@SerializedName("cargo")
		@Expose
		public String cargo;
		@SerializedName("pacientes")
		@Expose
		public ArrayList<PacienteReq> pacientes;
		
		public String toString(){
			return String.format("Req: %s[%d] (req0: pac[%d] = %s", this.cargo, this.id, this.pacientes.get(0).id, this.pacientes.get(0).requerimiento);
		}
	}
	
	public ArrayList<Req> requerimientos;
}
