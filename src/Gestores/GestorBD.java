package Gestores;


import Modelo.*;
import com.opencsv.CSVReader;
import com.sun.org.apache.regexp.internal.RE;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.sql.JDBCType.ARRAY;

/**
 * Created by Javier on 2/19/2018.
 */
public class GestorBD {

    private Connection conexion;
    private Statement estado;

    public GestorBD() {
        conexion = null;
        estado = null;
    }

    public void establecerConexionSuperUsuario() {

        try {
            Class.forName("org.postgresql.Driver");
            String connectionUrl = "jdbc:postgresql://127.0.0.1:5432/BaseSubastas?currentSchema=\"PRINCIPALSCHEMA\"";
            conexion = DriverManager.getConnection(connectionUrl,"postgres","jagergato");
            estado = conexion.createStatement();

        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConexion() {
        return conexion;
    }

    public Statement getEstado() {
        return estado;
    }

    public void cerrarConexion() {
        try {
            if (conexion != null) {
                conexion.close();
                conexion = null;
                estado = null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void establecerConexionUsuario(String username, String password) {

        try {
            Class.forName("org.postgresql.Driver");
            String connectionUrl = "jdbc:postgresql://127.0.0.1:5432/BaseSubastas?currentSchema= \"PRINCIPALSCHEMA\"";
            conexion = DriverManager.getConnection(connectionUrl,username,password);
            estado = conexion.createStatement();
            conexion.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public boolean existeConexionUsuarios(String username, String password) {
        Connection connection = null;
        Statement statement = null;
        try {
            Class.forName("org.postgresql.Driver");
            String connectionUrl = "jdbc:postgresql://127.0.0.1:5432/BaseSubastas?currentSchema=\"PRINCIPALSCHEMA\"";
            conexion = DriverManager.getConnection(connectionUrl,username,password);
            conexion.createStatement();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;

    }

    public boolean existeEntidad(String usuario,String tablaBuscar) {
        establecerConexionSuperUsuario(); // Para cuando se valida un supervisor
        String sqlEntidad = "";
        switch (tablaBuscar) {

            case "ADMINISTRADOR":
                sqlEntidad = "SELECT * FROM \"PRINCIPALSCHEMA\"." + tablaBuscar + " WHERE ALIASADMINISTRADOR = ?";
                break;
            default:
                sqlEntidad = "SELECT * FROM \"PRINCIPALSCHEMA\"." + tablaBuscar + " WHERE ALIASPARTICIPANTE = ?";
                break;
        }

        try {

            PreparedStatement obtenerEntidad = conexion.prepareStatement(sqlEntidad);
            obtenerEntidad.setString(1, usuario);
            ResultSet resultados = obtenerEntidad.executeQuery();

            if (resultados.next()) {
                cerrarConexion();
                return true;
            }

            obtenerEntidad.close();
            resultados.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        cerrarConexion();
        return false;
    }

    public void invocarAlerta(String mensaje) {

        Alert nuevaAlerta = new Alert(Alert.AlertType.WARNING);
        nuevaAlerta.setTitle("Error");
        nuevaAlerta.setContentText(mensaje);
        nuevaAlerta.showAndWait();

    }

    public void agregarNuevoUsuario(String usuario, String contrasenna, String cedula, String nombreApellidos, String direccion,ArrayList<String> telefonos,String tipoUsuario, String correo){
        String [] telefonosUsuario = new String[telefonos.size()];
        telefonosUsuario = telefonos.toArray(telefonosUsuario);


        String procedimientoAlmacenado = "";

        switch(tipoUsuario){
            case "Administrador":
                procedimientoAlmacenado = "{call \"PRINCIPALSCHEMA\".crearUsuarioAdministrador (?,?,?,?,?,?,?)}";
                break;
            case "Participante":
                procedimientoAlmacenado = "{call \"PRINCIPALSCHEMA\".crearUsuarioParticipante (?,?,?,?,?,?,?)}";
                break;
        }

       try{
            conexion.setAutoCommit(true);
            Array arregloTelefonos = conexion.createArrayOf("varchar",telefonosUsuario);


            CallableStatement agregarUsuario = conexion.prepareCall(procedimientoAlmacenado);
            agregarUsuario.setString(1,usuario);
            agregarUsuario.setString(2,contrasenna);
            agregarUsuario.setString(3,cedula);
            agregarUsuario.setString(4,nombreApellidos);
            agregarUsuario.setString(5,direccion);
            agregarUsuario.setArray(6,arregloTelefonos);
            agregarUsuario.setString(7, correo);

            agregarUsuario.executeUpdate();

            agregarUsuario.close();


           conexion.setAutoCommit(false);


        }catch(SQLException e){
            invocarAlerta("El usuario o cedula seleccionados ya han sido elegidos. Intente de nuevo.");
            e.printStackTrace();
            toggleAutoCommit(false);
        }


    }

    public void modificarUsuario(String alias,String nuevaCedula, String nuevoNombreApellidos, String nuevaDireccion){
        try{
            conexion.setAutoCommit(true);

            CallableStatement modificacionUsuario = conexion.prepareCall("{call \"PRINCIPALSCHEMA\".modificarUsuario(?,?,?,?)}");
            modificacionUsuario.setString(1,alias);
            modificacionUsuario.setString(2,nuevaCedula);
            modificacionUsuario.setString(3,nuevoNombreApellidos);
            modificacionUsuario.setString(4,nuevaDireccion);

            modificacionUsuario.executeUpdate();

            modificacionUsuario.close();

            conexion.setAutoCommit(false);

        }catch(SQLException e){
            invocarAlerta("La nueva cedula ya existe. Intente de nuevo.");
            e.printStackTrace();
            toggleAutoCommit(false);
        }
    }

    public ArrayList<String> devolverUsuarios(String alias,int atributo){
        ArrayList<String> aliasTelefonosUsuarios = new ArrayList<>();
        try{
            String sqlUsuarios = "{call \"PRINCIPALSCHEMA\".retornarUsuarios(?,?,?)}";
            CallableStatement retornarUsuarios = conexion.prepareCall(sqlUsuarios);

            retornarUsuarios.setString(1,alias);
            retornarUsuarios.setInt(2,atributo);
            retornarUsuarios.registerOutParameter(3, Types.OTHER);

            retornarUsuarios.executeUpdate();
            ResultSet aliasTelefonosDevueltos = (ResultSet)retornarUsuarios.getObject(3);

            switch(atributo){
                case 0: // Devuelve todos los alias
                    while (aliasTelefonosDevueltos.next()) {
                            aliasTelefonosUsuarios.add(aliasTelefonosDevueltos.getString("ALIAS"));
                    }
                    break;

                case 1: //Devuelve todos los telefonos
                    while (aliasTelefonosDevueltos.next()) {
                        aliasTelefonosUsuarios.add(aliasTelefonosDevueltos.getString("TELEFONO"));
                    }
                    break;

                default: //Devuelve una lista de toda la info de un solo usuario
                    while (aliasTelefonosDevueltos.next()) {
                        aliasTelefonosUsuarios.add(aliasTelefonosDevueltos.getString("CEDULA"));
                        aliasTelefonosUsuarios.add(aliasTelefonosDevueltos.getString("NOMBRE_APELLIDOS"));
                        aliasTelefonosUsuarios.add(aliasTelefonosDevueltos.getString("DIRECCION"));
                    }

            }

            retornarUsuarios.close();
            aliasTelefonosDevueltos.close();

        }catch(SQLException e){
            e.printStackTrace();
        }
        return aliasTelefonosUsuarios;
    }

    public void eliminarTelefonoUsuario(String aliasUsuario, String telefonoEliminar){
        String procTelefono = "{call \"PRINCIPALSCHEMA\".eliminarTelefonoUsuario(?,?)}";

        try{
            conexion.setAutoCommit(true);
            CallableStatement eliminarTelefono = conexion.prepareCall(procTelefono);
            eliminarTelefono.setString(1,aliasUsuario);
            eliminarTelefono.setString(2,telefonoEliminar);

            eliminarTelefono.executeUpdate();

            eliminarTelefono.close();

            conexion.setAutoCommit(false);
        }catch(SQLException e){
            e.printStackTrace();
            toggleAutoCommit(false);
        }
    }

    public void modificarTelefonoUsuario(String alias, String numeroTelefonoNuevo, String numeroTelefonoViejo){
        String procModificarTelefono = "{call \"PRINCIPALSCHEMA\".modificarTelefonoUsuario(?,?,?)}";

        try{
            conexion.setAutoCommit(true);
            CallableStatement modificarTelefono = conexion.prepareCall(procModificarTelefono);
            modificarTelefono.setString(1,alias);
            modificarTelefono.setString(2,numeroTelefonoNuevo);
            modificarTelefono.setString(3,numeroTelefonoViejo);

            modificarTelefono.executeUpdate();

            modificarTelefono.close();

            conexion.setAutoCommit(false);
        }catch(SQLException e){
            invocarAlerta("El nuevo telefono ya existe en la base de datos. Intente de nuevo.");
            e.printStackTrace();
            toggleAutoCommit(false);
        }
    }

    public void agregarNuevoTelefonoUsuario(String aliasUsuario,String nuevoTelefono){
        String procNuevoTelefono = "{call \"PRINCIPALSCHEMA\".agregarNuevoTelefonoUsuario(?,?)}";
        try{
            conexion.setAutoCommit(true);
            CallableStatement agregarTelefono = conexion.prepareCall(procNuevoTelefono);
            agregarTelefono.setString(1,aliasUsuario);
            agregarTelefono.setString(2,nuevoTelefono);

            agregarTelefono.executeUpdate();

            agregarTelefono.close();

            conexion.setAutoCommit(false);

        }catch(SQLException e){
            invocarAlerta("El telefono ingresadoya existe en la base de datos. Intente de nuevo.");
            e.printStackTrace();
            toggleAutoCommit(false);
        }
    }


    public void agregarNuevasVariables(String aliasAdministrador, BigDecimal porcentajeMejora, BigDecimal incrementoMinimo){

        String procedimientoVaribles = "{call \"PRINCIPALSCHEMA\".agregarNuevasVariables(?,?,?)}";

        try{
            conexion.setAutoCommit(true);
            CallableStatement agregarVariables = conexion.prepareCall(procedimientoVaribles);
            agregarVariables.setString(1,aliasAdministrador);
            agregarVariables.setBigDecimal(2,porcentajeMejora);
            agregarVariables.setBigDecimal(3,incrementoMinimo);

            agregarVariables.executeUpdate();

            agregarVariables.close();
            conexion.setAutoCommit(false);
        }catch(SQLException e){
            e.printStackTrace();
            toggleAutoCommit(false);
        }
    }


    public void crearSubasta(String aliasVendedor, Timestamp tiempoInicio, Timestamp tiempoFin, String descripcionItem,
                             String nombreImagen, BigDecimal precioBase, String detallesEntrega, int idSubcategoria){ // El id del item se obtiene en el stored procedure

        String subastaSQL = "{call \"PRINCIPALSCHEMA\".crearSubasta(?,?,?,?,?,?,?,?)}"; //INSERT INTO ITEM(DESCRIPCION,FOTO,PRECIO_BASE,DETALLESENTREGA,IDSUBCATEGORIA) VALUES(?,?,?,?,?);
        try {
            conexion.setAutoCommit(true);
            FileInputStream imagen = new FileInputStream("Imagenes/"+nombreImagen);

            CallableStatement nuevaSubasta = conexion.prepareCall(subastaSQL);


            nuevaSubasta.setString(1,aliasVendedor);
            nuevaSubasta.setTimestamp(2, tiempoInicio);
            nuevaSubasta.setTimestamp(3,tiempoFin);
            nuevaSubasta.setString(4,descripcionItem);
            nuevaSubasta.setBinaryStream(5,imagen,imagen.available());

            nuevaSubasta.setBigDecimal(6,precioBase);
            nuevaSubasta.setString(7,detallesEntrega);
            nuevaSubasta.setInt(8,idSubcategoria);

            nuevaSubasta.executeUpdate();

            nuevaSubasta.close();
            conexion.setAutoCommit(false);
        }catch(Exception e){
            e.printStackTrace();
            toggleAutoCommit(false);
        }
    }

    public ArrayList<String> getCategorias(){ //Con la modalidad devuelvo la categoria o subcategorias asciadas
        ArrayList<String> categorias = new ArrayList<>();
        String sqlCategorias = "{call \"PRINCIPALSCHEMA\".obtenerCategorias(?)}";

        try{
            CallableStatement ejecutarCat = conexion.prepareCall(sqlCategorias);
            ejecutarCat.registerOutParameter(1,Types.OTHER);

            ejecutarCat.executeUpdate();

            ResultSet catObtenidas = (ResultSet) ejecutarCat.getObject(1);
            while(catObtenidas.next()){

                    categorias.add(catObtenidas.getString("ID")+"-"+catObtenidas.getString("DESCRIPCION"));
            }

            ejecutarCat.close();
            catObtenidas.close();

        }catch(SQLException e){
            e.printStackTrace();
        }
        return categorias;
    }

    public ArrayList<String> filtrarSubCategorias(int idCategoria){
        ArrayList<String> subCategorias = new ArrayList<String>();
        String sqlFiltro = "{call \"PRINCIPALSCHEMA\".filtrarSubCategorias(?,?)}";
        try{
            CallableStatement filtrar = conexion.prepareCall(sqlFiltro);
            filtrar.setInt(1,idCategoria);
            filtrar.registerOutParameter(2,Types.OTHER);
            filtrar.executeUpdate();

            ResultSet subCategoriasObtenidas = (ResultSet) filtrar.getObject(2);
            while(subCategoriasObtenidas.next()){
                subCategorias.add(subCategoriasObtenidas.getString("IDSUB")+"-"+subCategoriasObtenidas.getString("DESCRIPCION"));
            }
            subCategoriasObtenidas.close();
            filtrar.close();
        }catch(SQLException e){
            e.printStackTrace();
        }

        return subCategorias;
    }

    public ArrayList<Subasta> getSubastas(Date fechaSistema, String aliasVendedor){
        String sqlSubastasBuenas = "{call \"PRINCIPALSCHEMA\".getSubastasValidas(?,?,?)}";
        ArrayList<Subasta> subastas = new ArrayList<>();
        try{
            CallableStatement subastasBuenas = conexion.prepareCall(sqlSubastasBuenas);
            subastasBuenas.setTimestamp(1,new Timestamp(fechaSistema.getTime()));
            subastasBuenas.setString(2,aliasVendedor);
            subastasBuenas.registerOutParameter(3,Types.OTHER);

            subastasBuenas.executeUpdate();

            ResultSet subastasDevueltas = (ResultSet) subastasBuenas.getObject(3);

            while(subastasDevueltas.next()){
                Subasta subastaAuxiliar = new Subasta(subastasDevueltas.getString("ID"),subastasDevueltas.getString("ALIASVENDEDOR"),
                        subastasDevueltas.getString("PRECIO_BASE"),subastasDevueltas.getString("DESCRIPCION"));

                subastas.add(subastaAuxiliar);
            }

            subastasBuenas.close();
            subastasDevueltas.close();
        }catch(SQLException e){
            e.printStackTrace();
        }
        return subastas;
    }

    public ArrayList<Subasta> getSubastasPorCategoria(Date fechaSistema, String aliasVendedor, int idCategoria,int modalidad){
        String sqlSubastasBuenasPorCategoria = "{call \"PRINCIPALSCHEMA\".getSubastasPorCategoria(?,?,?,?,?)}";
        ArrayList<Subasta> subastasPorCategoria = new ArrayList<>();
        try{
            CallableStatement subastasBuenasPorCategoria = conexion.prepareCall(sqlSubastasBuenasPorCategoria);
            subastasBuenasPorCategoria.setTimestamp(1,new Timestamp(fechaSistema.getTime()));
            subastasBuenasPorCategoria.setString(2,aliasVendedor);
            subastasBuenasPorCategoria.setInt(3,idCategoria);
            subastasBuenasPorCategoria.setInt(4,modalidad);
            subastasBuenasPorCategoria.registerOutParameter(5,Types.OTHER);

            subastasBuenasPorCategoria.executeUpdate();

            ResultSet subastasDevueltas = (ResultSet) subastasBuenasPorCategoria.getObject(5);

            while(subastasDevueltas.next()){
                Subasta subastaAuxiliar = new Subasta(subastasDevueltas.getString("ID"),subastasDevueltas.getString("ALIASVENDEDOR"),
                        subastasDevueltas.getString("PRECIO_BASE"),subastasDevueltas.getString("DESCRIPCION"));

                subastasPorCategoria.add(subastaAuxiliar);
            }

            subastasBuenasPorCategoria.close();
            subastasDevueltas.close();

        }catch(SQLException e){
            e.printStackTrace();
        }
        return subastasPorCategoria;
    }

    public void pujarPuja(String aliasComprador, int idItem, BigDecimal ofertaComprador, Timestamp fechaPuja){
       String sqlPujar = "{call \"PRINCIPALSCHEMA\".crearPuja(?,?,?,?)}";
       try{
           conexion.setAutoCommit(true);

           CallableStatement pujar = conexion.prepareCall(sqlPujar);
           pujar.setString(1,aliasComprador);
           pujar.setInt(2,idItem);
           pujar.setBigDecimal(3,ofertaComprador);
           pujar.setTimestamp(4,fechaPuja);
           pujar.executeUpdate();

           pujar.close();

           conexion.setAutoCommit(false);
       }catch(SQLException e){
           invocarAlerta("El monto ingresado debe ser mayor.");
           e.printStackTrace();
           toggleAutoCommit(false);
       }
    }

    public int buscarIdItem(int idSubasta){
        String sqlItem = "{call \"PRINCIPALSCHEMA\".buscarIdItem(?,?)}";
        int idItemDevuelto = 0;

        try{
            CallableStatement buscarIdItem = conexion.prepareCall(sqlItem);
            buscarIdItem.setInt(1,idSubasta);
            buscarIdItem.registerOutParameter(2,Types.OTHER);
            buscarIdItem.executeUpdate();

            ResultSet idDevuelto = (ResultSet) buscarIdItem.getObject(2);

            while(idDevuelto.next()){
                idItemDevuelto = Integer.parseInt(idDevuelto.getString("IDITEM"));
            }

            buscarIdItem.close();
            idDevuelto.close();

        }catch (SQLException e){
            e.printStackTrace();
        }
        return idItemDevuelto;
    }

    public Item extraerInformacionItem(String idSubasta){
        String sqlItem = "{call \"PRINCIPALSCHEMA\".extraerItem(?,?)}";
        Item itemEncontrado = null;
        try{
            CallableStatement buscarItem = conexion.prepareCall(sqlItem);
            buscarItem.setInt(1,Integer.parseInt(idSubasta));
            buscarItem.registerOutParameter(2,Types.OTHER);
            buscarItem.executeUpdate();

            ResultSet itemDevuelto = (ResultSet) buscarItem.getObject(2);

            while(itemDevuelto.next()){
                String  idItem = itemDevuelto.getString("IDITEM");
                String descripcionItem = itemDevuelto.getString("DESCRIPCION");
                String detallesEntrega = itemDevuelto.getString("DETALLESENTREGA");
                String precioItem = String.valueOf(itemDevuelto.getBigDecimal("PRECIO_BASE"));
                String tiempoInicio = fechaFormateada(itemDevuelto.getTimestamp("TIEMPOINICIO"));
                String tiempoFin = fechaFormateada(itemDevuelto.getTimestamp("TIEMPOFIN"));
                String nombreImagen = cargarImagen(itemDevuelto.getBytes("FOTO"),idItem);

                itemEncontrado = new Item(idItem,descripcionItem,detallesEntrega,nombreImagen,precioItem,tiempoInicio,tiempoFin);
            }

            buscarItem.close();
            itemDevuelto.close();
        }catch(SQLException e){
            e.printStackTrace();
        }
        return itemEncontrado;
    }

    public String fechaFormateada(Timestamp fechaActual){
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(fechaActual);
    }

    public String cargarImagen(byte [] imagenData,String idItem){
        String nombre = "Imagenes/Item"+idItem+".jpg";
        try{
                FileOutputStream fout = new FileOutputStream(nombre);
                fout.write(imagenData);
                fout.close();
            }
            catch (Exception e){
            e.printStackTrace();
        }
        return nombre;
    }

    public ArrayList<Puja> getPujas(int idSubasta){
        ArrayList<Puja> pujas = new ArrayList<>();
        String sqlPujas = "{call \"PRINCIPALSCHEMA\".getPujas(?,?)}";

        try{
            CallableStatement ejecutarPujas = conexion.prepareCall(sqlPujas);
            ejecutarPujas.setInt(1,idSubasta);
            ejecutarPujas.registerOutParameter(2,Types.OTHER);

            ejecutarPujas.executeUpdate();

            ResultSet pujasObtenidas = (ResultSet) ejecutarPujas.getObject(2);

            while(pujasObtenidas.next()){
                String idPuja = pujasObtenidas.getString("ID");
                String comprador = pujasObtenidas.getString("ALIASCOMPRADOR");
                String fechaHora = fechaFormateada(pujasObtenidas.getTimestamp("FECHA_HORA"));
                String montoOfrecido = String.valueOf(pujasObtenidas.getBigDecimal("PRECIO_OFERTA"));

                pujas.add(new Puja(idPuja,comprador,fechaHora,montoOfrecido));
            }

            ejecutarPujas.close();
            pujasObtenidas.close();
        }catch (SQLException e){
            e.printStackTrace();
        }
        return pujas;
    }

    public ArrayList<Subasta> getSubastasSinRestriccion(){
        ArrayList<Subasta> subastasSinRestriccion = new ArrayList<>();
        String sqlSubastasSinRestriccion = "{call \"PRINCIPALSCHEMA\".getSubastasSinRestriccion(?)}";

        try{
            CallableStatement ejecutarSubastasSin = conexion.prepareCall(sqlSubastasSinRestriccion);
            ejecutarSubastasSin.registerOutParameter(1,Types.OTHER);

            ejecutarSubastasSin.executeUpdate();

            ResultSet subastasObtenidas = (ResultSet) ejecutarSubastasSin.getObject(1);

            while(subastasObtenidas.next()){
                String idSubasta = subastasObtenidas.getString("ID");
                String vendedor = subastasObtenidas.getString("ALIASVENDEDOR");
                String precioBase = String.valueOf(subastasObtenidas.getBigDecimal("PRECIO_BASE"));
                String subCategoria = subastasObtenidas.getString("DESCRIPCION");
                subastasSinRestriccion.add(new Subasta(idSubasta,vendedor,precioBase,subCategoria));
            }

            ejecutarSubastasSin.close();
            subastasObtenidas.close();
        }catch (SQLException e){
            e.printStackTrace();
        }
        return subastasSinRestriccion;
    }

    public java.util.Date obtenerFecha(){
        java.util.Date fechaSistemaReal = null;
        try{
            DateFormat formatoFecha = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ENGLISH);
            java.util.Date objetoDate = new java.util.Date();
            String fechaSistema = formatoFecha.format(objetoDate);
            fechaSistemaReal = formatoFecha.parse(fechaSistema);

        }catch(Exception e){
            e.printStackTrace();
        }

        return fechaSistemaReal;
    }

    public ArrayList<ConsultasHistorial> obtenerHistorialSubastas(String aliasUsuario){
        ArrayList<ConsultasHistorial> consultasHistorial = new ArrayList<>();
        String sqlSubastasHistorial = "{call \"PRINCIPALSCHEMA\".obtenerHistorialSubastas(?,?)}";

        try{
            CallableStatement ejecutarSubastasHistorial = conexion.prepareCall(sqlSubastasHistorial);
            ejecutarSubastasHistorial.setString(1,aliasUsuario);
            ejecutarSubastasHistorial.registerOutParameter(2,Types.OTHER);

            ejecutarSubastasHistorial.executeUpdate();

            ResultSet tuplasSubastas = (ResultSet) ejecutarSubastasHistorial.getObject(2);

            while(tuplasSubastas.next()){
                String descripcionItem = tuplasSubastas.getString("DESCRIPCION");
                String precioBase = String.valueOf(tuplasSubastas.getBigDecimal("PRECIO_BASE"));
                String precioFinal =  (String.valueOf(tuplasSubastas.getBigDecimal("PRECIO_OFERTA")).equals("null"))? "No Disponible": String.valueOf(tuplasSubastas.getBigDecimal("PRECIO_OFERTA"));

                consultasHistorial.add(new ConsultasHistorial(descripcionItem,precioBase,precioFinal));

            }

            ejecutarSubastasHistorial.close();
            tuplasSubastas.close();

        }catch(SQLException e){
            e.printStackTrace();
        }

        return consultasHistorial;
    }

    public ArrayList<ConsultasHistorial> obtenerHistorialPujas(String usuario){
        ArrayList<ConsultasHistorial> resultadoHistorialPujas = null;
        try {
            String sqlHistorialPujas = "{call \"PRINCIPALSCHEMA\".obtenerHistorialPujas(?,?)}";
            resultadoHistorialPujas = new ArrayList<>();
            CallableStatement historialPujas = conexion.prepareCall(sqlHistorialPujas);
            historialPujas.setString(1, usuario);
            historialPujas.registerOutParameter(2, Types.OTHER);

            historialPujas.executeUpdate();

            ResultSet pujasDevueltas = (ResultSet) historialPujas.getObject(2);

            while (pujasDevueltas.next()){
                ConsultasHistorial tupla =
                        new ConsultasHistorial(
                                pujasDevueltas.getString("DESCRIPCION"),
                                pujasDevueltas.getString("PRECIO_BASE"),
                                pujasDevueltas.getString("PRECIO_OFERTA")
                        );
                resultadoHistorialPujas.add(tupla);
            }

            historialPujas.close();
            pujasDevueltas.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultadoHistorialPujas;
    }

    public Timestamp obtenerTiempoFin(int idSubasta){
        String sqlTiempoFin = "{call \"PRINCIPALSCHEMA\".obtenerTiempoFin(?,?)}";
        Timestamp tiempoFin = null;
        try{
            CallableStatement ejecutarTiempoFin  = conexion.prepareCall(sqlTiempoFin);
            ejecutarTiempoFin.setInt(1,idSubasta);
            ejecutarTiempoFin.registerOutParameter(2,Types.OTHER);

            ejecutarTiempoFin.executeUpdate();

            ResultSet tiempoObtenido = (ResultSet)ejecutarTiempoFin.getObject(2);
            while (tiempoObtenido.next()){
                tiempoFin = tiempoObtenido.getTimestamp("TIEMPOFIN");
            }

            if(tiempoFin!= null) {
                DateFormat formatoFecha = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
                String fechaFin= formatoFecha.format(tiempoFin);
                tiempoFin =new Timestamp(formatoFecha.parse(fechaFin).getTime());

                System.out.println(fechaFin);

            }

            ejecutarTiempoFin.close();
            tiempoObtenido.close();

        }catch (Exception e){
            e.printStackTrace();
        }

        return tiempoFin;

    }

    public ArrayList<Comentario> obtenerComentariosSobreUsuario(String usuario, String modalidad){
        ArrayList<Comentario> resultadoConsulta = new ArrayList<>();
        try {
            String sqlConsulta = "{call \"PRINCIPALSCHEMA\".obtenerComentarios(?,?,?)}";
            CallableStatement consulta = conexion.prepareCall(sqlConsulta);
            consulta.setString(1, usuario);
            consulta.setString(2, modalidad);
            consulta.registerOutParameter(3, Types.OTHER);

            consulta.executeUpdate();

            ResultSet comentariosDevueltos = (ResultSet) consulta.getObject(3);
            while(comentariosDevueltos.next()){
                Comentario tupla = new Comentario(
                        comentariosDevueltos.getString("DE"),
                        comentariosDevueltos.getString("DESCRIPCION"),
                        comentariosDevueltos.getString("COMENTARIO")
                );
                resultadoConsulta.add(tupla);
            }

            consulta.close();
            comentariosDevueltos.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return resultadoConsulta;
    }

    public void toggleAutoCommit(boolean valor){
        try {
            conexion.setAutoCommit(valor);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*public void cargarCat() {
        establecerConexionSuperUsuario();

        String csvFile = "C:/Users/Javier/Desktop/Bases de Datos II/Proyectos/Proyecto 1/categorias.csv";
  //      BufferedReader reader = null;
        String [] line = null;
        String cvsSplitBy = ",";
        String categoriaActual = "";
        int idCategoria = 0;
        CSVReader reader = null;
        int cont = 0;
        try {
             reader = new CSVReader(new FileReader(csvFile), ',', '"');
          //  reader= new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), "UTF-8"));
                  //  br = new BufferedReader(new FileReader(csvFile, ));
           // br.readLine();
            reader.readNext();
            while ((line = reader.readNext()) != null) {
                if(!line[0].equals(""))
                    System.out.println(line[0] + "    " +  line[1]);

                if(cont ==1){
                    categoriaActual= line[0]; //Agarra la primer categoria
                    String sql = "INSERT INTO \"PRINCIPALSCHEMA\".CATEGORIA(DESCRIPCION) VALUES(?)";
                    String returnCols[] = { "id" };
                    PreparedStatement caca = conexion.prepareStatement(sql, returnCols);
                    caca.setString(1,categoriaActual);
                    caca.execute();
                    ResultSet llave = caca.getGeneratedKeys();

                    while(llave.next()){
                        idCategoria = llave.getInt(1);
                    }
                    llave.close();
                    caca.close();
                    insertarSubcategoria(idCategoria, line[1]);
                }

                String[] country = line;

                if(!country[0].equals("") && cont!=1){
                    if(categoriaActual.equals(country[0])){
                       insertarSubcategoria(idCategoria,country[1]);
                    }
                    else{
                        categoriaActual= line[0];
                        String sql2 = "INSERT INTO \"PRINCIPALSCHEMA\".CATEGORIA(DESCRIPCION) VALUES(?)";
                        String returnCols[] = { "id" };
                        PreparedStatement caca2 = conexion.prepareStatement(sql2, returnCols);
                        caca2.setString(1,categoriaActual);
                        caca2.execute();
                        ResultSet llave2 = caca2.getGeneratedKeys();

                        while(llave2.next()){
                            idCategoria = llave2.getInt(1);
                        }
                        llave2.close();
                        caca2.close();
                        insertarSubcategoria(idCategoria, line[1]);
                    }

                }

                cont++;

            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void  insertarSubcategoria(int idCategoria, String descripcionSub){
        if(conexion==null)
            establecerConexionSuperUsuario();
        String s = "{call \"PRINCIPALSCHEMA\".pruebaImagen(?,?)}";


        try{
            CallableStatement c = conexion.prepareCall(s);
            c.setInt(1,idCategoria);
            c.setString(2,descripcionSub);
            c.executeUpdate();

            c.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }*/

}