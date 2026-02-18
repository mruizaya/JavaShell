Nombres del equipo (D2L):
- Isabella Tulcan
- Luis Eduardo Zaldumbide
- Martin Ruiz

Contribuciones:
- Isabella Tulcan:integracion Docker (Dockerfile, estructura de carpetas/archivos, iproute2). 
- Luis Eduardo Zaldumbide: implementacion del shell jsh en Java (parsing, builtins, historial, => y ^^).
- Martin Ruiz: pruebas, verificacion de requisitos y apoyo en ajustes finales de salida/formato.

Comentarios/observaciones:
- Repositorio GitHub: https://github.com/miatulcan/JavaShell
- Imagen Docker: miatulcan/jsh:javashell
- Comandos usados para publicar la imagen:
  docker tag jsh miatulcan/jsh:javashell
  docker push miatulcan/jsh:javashell

Pasos para abrir y ejecutar el trabajo (asumiendo solo Git y Docker instalados):
1) Clonar el repositorio:
   git clone https://github.com/miatulcan/JavaShell
   cd JavaShell

2) Construir la imagen localmente (opcion A, desde Dockerfile):
   docker build -t miatulcan/jsh:javashell .

3) Ejecutar el contenedor:
   docker run -it --rm miatulcan/jsh:javashell

4) Probar dentro del shell:
   - Ver prompt con ruta actual: jsh:/ruta>>
   - Probar builtins: pwd, cd /folder1, history, exit
   - Probar comandos externos: ls, echo "hola mundo", ip addr, sleep 1, date
   - Probar secuencia en primer plano: echo hola => sleep 1 => date
   - Probar background: sleep 3 ^^ echo "bg" ^^

Opcional (si prefiere descargar imagen ya publicada):
   docker pull miatulcan/jsh:javashell
   docker run -it --rm miatulcan/jsh:javashell
