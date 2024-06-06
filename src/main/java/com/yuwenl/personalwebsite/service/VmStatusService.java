package com.yuwenl.personalwebsite.service;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.yuwenl.personalwebsite.entity.VmStatus;
import com.yuwenl.personalwebsite.entity.DailyPerformance;
import com.yuwenl.personalwebsite.repository.VmStatusRepository;
import com.yuwenl.personalwebsite.repository.DailyPerformanceRepository;

import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VmStatusService {

    @Autowired
    private VmStatusRepository vmStatusRepository;

    @Autowired
    private DailyPerformanceRepository dailyPerformanceRepository;

    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeMXBean;
    private static final long TOTAL_MEMORY = 16 * 1024 * 1024 * 1024L; // 16GB in bytes

    public VmStatusService() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    }

    public void recordVmStatus() {
        LocalDateTime startupTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(runtimeMXBean.getStartTime()), ZoneId.systemDefault());
        long uptime = runtimeMXBean.getUptime();
        long memoryUsed = osBean.getTotalMemorySize() - osBean.getFreeMemorySize();
        double cpuUsage = osBean.getCpuLoad() * 100;

        VmStatus vmStatus = new VmStatus();
        vmStatus.setStartupTime(startupTime);
        vmStatus.setUptime(uptime);
        vmStatus.setMemoryUsed(memoryUsed);
        vmStatus.setCpuUsage(cpuUsage);
        vmStatus.setTimestamp(LocalDateTime.now());
        vmStatusRepository.save(vmStatus);
    }

    public double getDailyPerformanceScore(LocalDateTime day) {
        LocalDateTime startOfDay = day.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<VmStatus> dailyRecords = vmStatusRepository.findAllByTimestampBetween(startOfDay, endOfDay);

        double totalCpuUsage = 0;
        double totalMemoryUsagePercentage = 0;

        for (VmStatus record : dailyRecords) {
            totalCpuUsage += record.getCpuUsage();
            totalMemoryUsagePercentage += getMemoryUsagePercentage(record.getMemoryUsed(), TOTAL_MEMORY);
        }

        int recordCount = dailyRecords.size();
        double averageCpuUsage = totalCpuUsage / recordCount;
        double averageMemoryUsagePercentage = totalMemoryUsagePercentage / recordCount;

        // Performance score is calculated based on the average CPU usage and memory usage percentage
        double performanceScore = averageCpuUsage * 0.6 + averageMemoryUsagePercentage * 0.4;

        // Save the daily performance score to the database
        DailyPerformance dailyPerformance = new DailyPerformance();
        dailyPerformance.setDate(day.toLocalDate());
        dailyPerformance.setPerformanceScore(performanceScore);
        dailyPerformanceRepository.save(dailyPerformance);

        return performanceScore;
    }

    private double getMemoryUsagePercentage(long usedMemory, long totalMemory) {
        return ((double) usedMemory / totalMemory) * 100;
    }

    public List<DailyPerformance> getLast60DaysPerformanceScores() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(60);
        return dailyPerformanceRepository.findAllByDateBetween(startDate, today);
    }

    public List<VmStatus> getLast30DaysVmStatus() {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime startDate = today.minusDays(30);
        List<VmStatus> vmStatuses = vmStatusRepository.findAllByTimestampBetween(startDate, today);

        return vmStatuses.stream().map(status -> {
            double memoryUsagePercentage = getMemoryUsagePercentage(status.getMemoryUsed(), TOTAL_MEMORY);
            status.setMemoryUsagePercentage(memoryUsagePercentage);
            return status;
        }).collect(Collectors.toList());
    }
}