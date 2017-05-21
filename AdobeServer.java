package adobe.server;

import java.net.*;
import java.util.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

 
public class AdobeServer
{
    //declaration of global variables
    private static Path rootpath;
    public static Path getrootpath() {
        return rootpath;
    }
    
    private static Map<String, String> headerMap = new HashMap();
    public static  Map<String, String> getHeaderMap(){
        return headerMap;
    }
    
    private static String Requesttype;
    public static String getRequesttype(){
        return Requesttype;
    }
          
    private static String UserPath;
    public static String getUserPath(){
        return UserPath;
    }
    
    private static String Protocol;
    public static String getProtocol(){
        return Protocol;
    }
    
    //...well... this is the main function. nothing spectacular 
    // it basically reads the arguments and creates a new server. thats it. 
    public static void main(String[] args)
    {
        //reads the first argument after the -d as default path to the FileSystems
        for(int i = 0; i < args.length; i++)
            {
                if(args[i] == null) { continue; }
                if(args[i].equals("-d")){
                    rootpath = new File(args[i + 1].toString()).toPath();
                }
            }
        AdobeServer adobeServer = new  AdobeServer();

    }
 
    //here i created a multithread server socket. 
    public AdobeServer()
    {
        //This is basically just a way to multithread. 
        try {
            ServerSocket sSocket = new ServerSocket(5000);
            System.out.println("Server wurde gestartet am: " + new Date());
             
            System.out.println("Waiting for client on port " +  sSocket.getLocalPort() + "...");
            //this is the loop that checks for incoming docking tries
            while(true) {
                //until someone docks to the socket, nothing happens. When someone docks, we accept the connection
                Socket socket = sSocket.accept();
                //we create a new thread for the new socket
                ClientThread cT = new ClientThread(socket); 
                //now we start the thread, multithreading done :D
                new Thread(cT).start();
                 
            }
        } catch(IOException exception) {
            System.out.println("Error: " + exception);
        }
    }
     
    //I implemented the ClientThread class as a Runnable, so it can be used as a thread
    class  ClientThread implements Runnable
    {
        Socket threadSocket;
         
        public ClientThread(Socket socket)
        {
            //Here i connect the socket to a local threadsocket as a local variable
            threadSocket = socket;
        }
         
        
        //this function parses the input data from a previously gathered String-array
        //all lines from a buffered reader are each in an array-field in the string so we can parse each array field seperately
        private Map<String, String> parseInputData(String[] data) {
            Map<String, String> out = new HashMap<String, String>();
        //for each array-field in the string-array we parse it
            for (String item : data) {
                if (item.indexOf("=") == -1) {
                    out.put(item, null);
                    continue;
                }

                String value = item.substring(item.indexOf('=') + 1);

                /*  here i tried to decode the input. It CAN be something strange according to http protocol, 
                so i made sure i can use all the information     */
                try {
                    value = URLDecoder.decode(value, "UTF-8");
                }
                catch (UnsupportedEncodingException e) {}

                out.put(item.substring(0, item.indexOf('=')), value);
            }
            return out;
        }
        
        //here we parse the request to see what comes in from what we read from the inputstream
        public void parseRequest() throws IOException, SocketException, Exception {
        // Used to read in from the socket
        BufferedReader input = new BufferedReader(new InputStreamReader(threadSocket.getInputStream()));
                        
        StringBuilder requestBuilder = new StringBuilder();

        /*  according to HTTP Spec (4.1) there can be empty lines at the start of each message, so i made sure to ignore them
     
            */
        
         
        String firstLine = input.readLine();
        if (firstLine == null) {
            throw new Exception("Input is returning nulls...");
        }

        while (firstLine.isEmpty()) {
            firstLine = input.readLine();
        }
        
        //in the first line, we get the request-type, path and the protocol type seperated with an empty space between them.
        //so we split the first line and put it in a string-array to access all the information
        String[] splittedLine = firstLine.trim().split(" ");
        
        // Set the request type
        Requesttype = splittedLine[0].toUpperCase();

        // set the path
        UserPath = splittedLine[1];

        // set the protocol type
        Protocol = splittedLine[2];
        
        requestBuilder.append(splittedLine);
        requestBuilder.append("\n");

        /*  After the first line, but before an empty line, we can assign some key:value pairs.
        we do that by again splitting the line at ": " and getting the key and second the corresponding value */
        for (String line = input.readLine(); line != null && !line.isEmpty(); line = input.readLine()) {
            requestBuilder.append(line);
            requestBuilder.append("\n");

            String[] items = line.split(": ");

            if (items.length == 1) {
                throw new Exception("No key value pair in \n\t" + line);
            }

            String value = items[1];
            for (int i = 2; i < items.length; i++) {
                value += ": " + items[i];
            }
            headerMap.put(items[0], value);
        }
            
        //now all the key-value pairs are stored in the headerMap and we also got the corresponding information what header we have

        
        
    }
        
        // this runs the whole server
        public void run()
        {
            
            try {
                //first we create all the streams that we need for this application
               
                
                //small message when the socket gets connected to
                System.out.println("Just connected to " + threadSocket.getRemoteSocketAddress());
                
                //dataoutputstream is for writing the Data (files etc.) to the connected client
                DataOutputStream out = new DataOutputStream(threadSocket.getOutputStream());
                
                //datainputstream is for reading out the Data into a stream to write to the outputstream
                DataInputStream in = new DataInputStream(threadSocket.getInputStream());
                
                //when the link is referencing to a directory we simply write send it with an outputstream
                OutputStreamWriter outstream = new OutputStreamWriter(threadSocket.getOutputStream());

                //we parse the request now
                parseRequest();

                Path path = FileSystems.getDefault().getPath(getUserPath());
                
                String outputdirectory[];
                String outputstring ="";


                //if the given path is a Directory, we read out all files + directories and then write them to the outputstream.
                
                if(path.toFile().isDirectory()){
                    outputdirectory = path.toFile().list();
                    
                    for (String output1 : outputdirectory) {
                        outputstring = outputstring + "\n" + output1;
                    } 
                    outstream.write(outputstring);                    
                }
                
                //if the given path is a File, we read the file with a FileInputstream and write that stream out into the outputstream.
                if(path.toFile().isFile()) {
                    URL url = new URL(rootpath.toString());
                    URLConnection connection = url.openConnection();
                    connection.setDoOutput(true);
                    try (FileInputStream fin = new FileInputStream(path.toFile())) {
                        int i;
                        while((i=fin.read())!=-1){
                            System.out.print((char)i);
                            out.writeByte(fin.read());
                        }
                    }
                }  
       
                
            } catch(IOException exception) {
                System.out.println("Error: " + exception);
            } catch (Exception ex) {
                Logger.getLogger(AdobeServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
            
            
            
        }
    }
}