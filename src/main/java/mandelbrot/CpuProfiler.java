package mandelbrot;

import java.util.Stack;

/**
 * Created by Pepper on 3/24/2015.
 */

public class CpuProfiler {
    private static int level;
    private static Stack<CpuProfileTask> taskStack;
    private static CpuProfileTask endingTask;

    static {
        taskStack = new Stack<>();
        level = 0;
    }

    public static void startTask(String operation){
        level++;
        taskStack.push(new CpuProfileTask(operation, System.nanoTime()));
    }

    public static void endTask(){
        endingTask = taskStack.pop();
        for(int i = 0; i<level; i++)
            System.out.print(" ");
        System.out.println(endingTask.getTaskName() + " " + ((System.nanoTime() - endingTask.getStartTime())/1_000) + " micro seconds " + ((System.nanoTime() - endingTask.getStartTime())/1_000_000) + "ms");
        level--;
    }
}

class CpuProfileTask {
    private String taskName;
    private long startTime;

    public CpuProfileTask(String name, long time){
        taskName = name;
        startTime = time;
    }

    public String getTaskName() {
        return taskName;
    }

    public long getStartTime() {
        return startTime;
    }
}
