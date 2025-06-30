public class Job {
    int id, burstTime, arrivalTime, startTime = -1, endTime = -1, remainingTime;
    String priority;

    public Job(int id, int burstTime, String priority, int arrivalTime) {
        this.id = id;
        this.burstTime = burstTime;
        this.priority = priority;
        this.arrivalTime = arrivalTime;
        this.remainingTime = burstTime;
    }

    public static Job copy(Job job) {
        return new Job(job.id, job.burstTime, job.priority, job.arrivalTime);
    }

    public int getPriorityValue() {
        return switch (priority) {
            case "High" -> 1;
            case "Medium" -> 2;
            case "Low" -> 3;
            default -> 4;
        };
    }
}
