# Imagen base con Java
FROM eclipse-temurin:17-jdk

# Instalar ip (para ip addr)
RUN apt update && apt install -y iproute2

# Crear estructura de carpetas obligatoria
RUN mkdir -p /folder1/folder2/folder3/folder4/folder5/folder6/folder7

# Crear archivos vacíos en cada carpeta
RUN touch /folder1/folder1.txt
RUN touch /folder1/folder2/folder2.txt
RUN touch /folder1/folder2/folder3/folder3.txt
RUN touch /folder1/folder2/folder3/folder4/folder4.txt
RUN touch /folder1/folder2/folder3/folder4/folder5/folder5.txt
RUN touch /folder1/folder2/folder3/folder4/folder5/folder6/folder6.txt
RUN touch /folder1/folder2/folder3/folder4/folder5/folder6/folder7/folder7.txt

# Directorio donde vivirá tu shell
WORKDIR /app

COPY Jsh.java .

RUN javac Jsh.java

CMD ["java", "Jsh"]





