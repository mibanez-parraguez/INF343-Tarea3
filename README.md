# INF343-Tarea3
Tarea 3 Sistemas Distribuidos

__Autores__
* Felipe Santander (201104528-9)
* Miguel Ibáñez (2990010-8)

---

* Para compilar (desde raíz): 
`$ ./gradlew installDist`

* Para ejecutar (raíz): 
`$ ./build/install/tarea3/bin/tarea3`

---

__Formato JSON usados__

sólo se cambió el json de requerimientos:

```
{"requerimientos": [
	{"id": 1,
	"cargo": "doctor",
	"pacientes": [ {"id": 1, "requerimiento": "recetar metformina"},
                     {"id": 3, "requerimiento": "pedir tomografia"},
        ]},
      
	{"id": 1,
	"cargo": "enfermero",
	"pacientes": [ {"id": 1, "requerimiento": "suministrar metformina"},
			]},
	...
	]
}
```
El resto de los archivos quedaron en su estructura original.

Además se usa el siguiente archivo.

```
{
  "id": 9,
  "puerto_bully": 30123,
  "puerto_logger": 30125,
  "puerto_req": 30127,
  "direccion": "dist09.inf.santiago.usm.cl",
  "hospitales": [
    {
      "id": 10,
      "puerto_bully": 30123,
      "puerto_logger": 30125,
      "puerto_req": 30127,
      "direccion": "dist10.inf.santiago.usm.cl"
    },
    {
      "id": 11,
      "puerto_bully": 30123,
      "puerto_logger": 30125,
      "puerto_req": 30127,
      "direccion": "dist11.inf.santiago.usm.cl"
    },
    {
      "id": 12,
      "puerto_bully": 30123,
      "puerto_logger": 30125,
      "puerto_req": 30127,
      "direccion": "dist12.inf.santiago.usm.cl"
    }
  ]
}

```
