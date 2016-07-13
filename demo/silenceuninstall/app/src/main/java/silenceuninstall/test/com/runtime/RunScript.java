package silenceuninstall.test.com.runtime;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class RunScript {
    private String command;
    private String stdout = null;
    private String stderr;
    private int retvalue;
    private boolean finished = false;
    private long time = 0;

    public RunScript(String command) {
        this.command = command;
    }

    public static String runIt(String command) {
        return new RunScript(command).run();
    }

    public synchronized String run() {
        Thread process = new Thread() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                try {
                    final Process m_process = Runtime.getRuntime().exec(command);
                    final StringBuilder sbread = new StringBuilder();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                            m_process.getInputStream()), 8192);
                    String ls_1 = null;

                    try {
                        while ((ls_1 = bufferedReader.readLine()) != null) {
                            sbread.append(ls_1).append("\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        finished = true;
                    } finally {
                        try {
                            bufferedReader.close();
                            m_process.getInputStream().close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }

                    stdout = sbread.toString();
                    retvalue = m_process.waitFor();
                    finished = true;
                } catch (IOException ee) {
                    System.err.println("RunScript have a IO error:" + ee.getMessage());
                    finished = true;
                } catch (InterruptedException ie) {
                    System.err.println("RunScript have a interrupte error:" + ie.getMessage());
                    finished = true;
                } catch (Exception ex) {
                    System.err.print("RunScript have a error :" + ex.getMessage());
                    finished = true;
                }
            }
        };

        process.start();

        while (!finished) {
            try {
                Thread.sleep(500);
                time = time + 500;

                // System.out.println("process state:"+process.getState());
                if (time > 8000) {
                    System.out.println("process interrupt send.....");
                    process.interrupt();
                    finished = true;
                    time = 0;
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return this.stdout;
    }

    public String getCommand() {
        return command;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public int getRetvalue() {
        return retvalue;
    }
}
