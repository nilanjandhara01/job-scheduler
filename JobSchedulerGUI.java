import javax.swing.*;
import java.awt.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class JobSchedulerGUI {
    private static final List<Job> jobList = new ArrayList<>();
    private static List<Job> lastRunJobs = new ArrayList<>();
    private static JTextArea outputArea;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(JobSchedulerGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Job Scheduler");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        JPanel panel = new JPanel(new GridLayout(7, 2, 5, 5));
        JTextField jobIdField = new JTextField();
        JTextField burstField = new JTextField();
        JTextField quantumField = new JTextField("2");

        JComboBox<String> priorityBox = new JComboBox<>(new String[]{"High", "Medium", "Low"});
        JComboBox<String> algorithmBox = new JComboBox<>(new String[]{"FCFS", "SJF", "Priority", "Round Robin"});
        JButton addButton = new JButton("Add Job");
        JButton runButton = new JButton("Run Scheduler");
        JButton exportButton = new JButton("Export to CSV");

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        panel.add(new JLabel("Job ID:"));
        panel.add(jobIdField);
        panel.add(new JLabel("Burst Time:"));
        panel.add(burstField);
        panel.add(new JLabel("Priority:"));
        panel.add(priorityBox);
        panel.add(new JLabel("Quantum (RR only):"));
        panel.add(quantumField);
        panel.add(new JLabel("Scheduling Algorithm:"));
        panel.add(algorithmBox);
        panel.add(addButton);
        panel.add(runButton);
        panel.add(exportButton);

        algorithmBox.addActionListener(e -> {
            boolean isRR = Objects.equals(algorithmBox.getSelectedItem(), "Round Robin");
            quantumField.setEnabled(isRR);
            quantumField.setText(isRR ? "2" : "");
        });

        addButton.addActionListener(e -> {
            try {
                int jobId = Integer.parseInt(jobIdField.getText().trim());
                int burst = Integer.parseInt(burstField.getText().trim());
                String priority = (String) priorityBox.getSelectedItem();

                if (burst <= 0) {
                    outputArea.append("Burst time must be > 0\n");
                    return;
                }

                ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
                LocalTime time = nowIST.toLocalTime();
                int arrivalInSeconds = time.toSecondOfDay();
                String timeString = time.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                jobList.add(new Job(jobId, burst, priority, arrivalInSeconds));
                outputArea.append("Added Job ID: " + jobId + ", Arrival: " + timeString + " IST (" + arrivalInSeconds + "s), Priority: " + priority + "\n");

                jobIdField.setText("");
                burstField.setText("");
            } catch (NumberFormatException ex) {
                outputArea.append("Invalid input!\n");
            }
        });

        runButton.addActionListener(e -> {
            if (jobList.isEmpty()) {
                outputArea.append("No jobs to schedule.\n");
                return;
            }

            String algo = (String) algorithmBox.getSelectedItem();
            List<Job> jobsCopy = jobList.stream().map(Job::copy).collect(Collectors.toList());
            lastRunJobs = jobsCopy;

            outputArea.append("\n--- " + algo + " Scheduling ---\n");

            switch (Objects.requireNonNull(algo)) {
                case "FCFS" -> runFCFS(jobsCopy);
                case "SJF" -> runSJF(jobsCopy);
                case "Priority" -> runPriority(jobsCopy);
                case "Round Robin" -> {
                    try {
                        int quantum = Integer.parseInt(quantumField.getText().trim());
                        runRoundRobin(jobsCopy, quantum);
                    } catch (NumberFormatException ex) {
                        outputArea.append("Invalid quantum value.\n");
                    }
                }
            }
        });

        exportButton.addActionListener(e -> {
            if (lastRunJobs.isEmpty()) {
                outputArea.append("No schedule to export.\n");
                return;
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter("schedule_output.csv"))) {
                writer.println("Job ID,Arrival Time (s),Burst Time,Priority,Start Time,End Time");
                for (Job job : lastRunJobs) {
                    if (job.startTime != -1) {
                        writer.printf("%d,%d,%d,%s,%s,%s\n",
                                job.id, job.arrivalTime, job.burstTime, job.priority,
                                secondsToIST(job.startTime), secondsToIST(job.endTime));
                    }
                }
                outputArea.append("Exported to schedule_output.csv\n");
            } catch (Exception ex) {
                outputArea.append("Error exporting CSV: " + ex.getMessage() + "\n");
            }
        });

        frame.getContentPane().add(panel, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static void runFCFS(List<Job> jobs) {
        jobs.sort(Comparator.comparingInt(j -> j.arrivalTime));
        int time = 0;
        for (Job job : jobs) {
            time = Math.max(time, job.arrivalTime);
            job.startTime = time;
            time += job.burstTime;
            job.endTime = time;
        }

        jobs.sort(Comparator.comparingInt(j -> j.arrivalTime)); // Sort by arrival
        for (Job job : jobs) {
            outputArea.append("Job " + job.id + " (" + job.priority + ") started at: " +
                    secondsToIST(job.startTime) + ", finished at: " +
                    secondsToIST(job.endTime) + "\n");
        }
    }

    private static void runSJF(List<Job> jobs) {
        jobs.sort(Comparator.comparingInt(j -> j.arrivalTime));
        List<Job> readyQueue = new ArrayList<>();
        int time = 0, index = 0;

        while (index < jobs.size() || !readyQueue.isEmpty()) {
            while (index < jobs.size() && jobs.get(index).arrivalTime <= time) {
                readyQueue.add(jobs.get(index++));
            }

            if (readyQueue.isEmpty()) {
                time = jobs.get(index).arrivalTime;
            } else {
                readyQueue.sort(Comparator.comparingInt(j -> j.burstTime));
                Job job = readyQueue.remove(0);
                job.startTime = time;
                time += job.burstTime;
                job.endTime = time;
            }
        }

        jobs.sort(Comparator.comparingInt(j -> j.burstTime)); // Sort by burst
        for (Job job : jobs) {
            outputArea.append("Job " + job.id + " (" + job.priority + ") started at: " +
                    secondsToIST(job.startTime) + ", finished at: " +
                    secondsToIST(job.endTime) + "\n");
        }
    }

    private static void runPriority(List<Job> jobs) {
        jobs.sort(Comparator.comparingInt(j -> j.arrivalTime));
        PriorityQueue<Job> pq = new PriorityQueue<>(Comparator.comparingInt(Job::getPriorityValue));
        int time = 0, index = 0;

        while (index < jobs.size() || !pq.isEmpty()) {
            while (index < jobs.size() && jobs.get(index).arrivalTime <= time) {
                pq.offer(jobs.get(index++));
            }

            if (pq.isEmpty()) {
                time = jobs.get(index).arrivalTime;
            } else {
                Job job = pq.poll();
                job.startTime = time;
                time += job.burstTime;
                job.endTime = time;
            }
        }

        jobs.sort(Comparator.comparingInt(Job::getPriorityValue)); // Sort by priority
        for (Job job : jobs) {
            outputArea.append("Job " + job.id + " (" + job.priority + ") started at: " +
                    secondsToIST(job.startTime) + ", finished at: " +
                    secondsToIST(job.endTime) + "\n");
        }
    }

    private static void runRoundRobin(List<Job> jobs, int quantum) {
        jobs.sort(Comparator.comparingInt(j -> j.arrivalTime));
        Queue<Job> queue = new LinkedList<>();
        int time = 0, index = 0;

        while (index < jobs.size() || !queue.isEmpty()) {
            while (index < jobs.size() && jobs.get(index).arrivalTime <= time) {
                queue.offer(jobs.get(index++));
            }

            if (queue.isEmpty()) {
                time = jobs.get(index).arrivalTime;
            } else {
                Job job = queue.poll();
                if (job.startTime == -1) job.startTime = time;
                int runTime = Math.min(quantum, job.remainingTime);
                time += runTime;
                job.remainingTime -= runTime;

                while (index < jobs.size() && jobs.get(index).arrivalTime <= time) {
                    queue.offer(jobs.get(index++));
                }

                if (job.remainingTime > 0) {
                    queue.offer(job);
                } else {
                    job.endTime = time;
                }
            }
        }

        // Output without sorting
        for (Job job : jobs) {
            outputArea.append("Job " + job.id + " (" + job.priority + ") started at: " +
                    secondsToIST(job.startTime) + ", finished at: " +
                    secondsToIST(job.endTime) + "\n");
        }
    }

    private static String secondsToIST(int seconds) {
        LocalTime base = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalTime().withSecond(0).withNano(0);
        LocalTime converted = base.plusSeconds(seconds);
        return converted.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
