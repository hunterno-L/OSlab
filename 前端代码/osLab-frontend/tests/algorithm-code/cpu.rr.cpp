// Round Robin. Input: n quantum, then n rows: name arrival burst.
// EOF uses A(0,10), B(0,8), C(1,7), D(2,13), quantum=2.
#include <algorithm>
#include <iomanip>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

using Time = long long;

struct Process {
    std::string name;
    Time arrival;
    Time burst;
    int priority = 0;
    Time firstStart = -1;
    Time completion = -1;
};

void sortByArrival(std::vector<Process>& jobs, bool usePriority = false) {
    // Keep the original pairwise exchange order, including its equal-time ties.
    for (std::size_t i = 0; i < jobs.size(); ++i) {
        for (std::size_t j = i + 1; j < jobs.size(); ++j) {
            if (jobs[i].arrival > jobs[j].arrival ||
                (usePriority && jobs[i].arrival == jobs[j].arrival &&
                 jobs[i].priority < jobs[j].priority)) {
                std::swap(jobs[i], jobs[j]);
            }
        }
    }
}

void printResults(const std::vector<Process>& jobs) {
    double sumTurnaround = 0.0;
    double sumWeighted = 0.0;
    std::cout << "\nname arrival burst first_start completion waiting turnaround weighted\n";
    for (const Process& job : jobs) {
        const Time turnaround = job.completion - job.arrival;
        const Time waiting = turnaround - job.burst;
        const double weighted = static_cast<double>(turnaround) / job.burst;
        sumTurnaround += turnaround;
        sumWeighted += weighted;
        std::cout << job.name << ' ' << job.arrival << ' ' << job.burst << ' '
                  << job.firstStart << ' ' << job.completion << ' ' << waiting << ' '
                  << turnaround << ' ' << std::fixed << std::setprecision(2)
                  << weighted << '\n';
    }
    if (!jobs.empty()) {
        std::cout << "average_turnaround " << sumTurnaround / jobs.size() << '\n'
                  << "average_weighted " << sumWeighted / jobs.size() << '\n';
    }
}

void validate(const std::vector<Process>& jobs) {
    for (const Process& job : jobs) {
        if (job.arrival < 0 || job.arrival > 1000000000LL ||
            job.burst <= 0 || job.burst > 1000000000LL) {
            throw std::runtime_error("arrival must be 0..1e9; burst must be 1..1e9");
        }
    }
}

std::vector<Process> readInput(Time& quantum) {
    int count;
    std::cin >> std::ws;
    if (std::cin.eof()) {
        return {{"A", 0, 10}, {"B", 0, 8}, {"C", 1, 7}, {"D", 2, 13}};
    }
    if (!(std::cin >> count)) throw std::runtime_error("expected a process count");
    if (count < 1 || count > 1000) throw std::runtime_error("process count must be 1..1000");
    if (!(std::cin >> quantum) || quantum < 1 || quantum > 1000000000LL)
        throw std::runtime_error("expected a positive quantum (at most 1e9)");
    std::vector<Process> jobs;
    for (int i = 0; i < count; ++i) {
        Process job;
        if (!(std::cin >> job.name >> job.arrival >> job.burst))
            throw std::runtime_error("incomplete process row");
        jobs.push_back(job);
    }
    validate(jobs);
    return jobs;
}

struct Slice {
    std::size_t process;
    Time readyAt;
    Time remaining;
};

void simulate(std::vector<Process> jobs, Time quantum) {
    // @trace:initialize
    sortByArrival(jobs);
    std::vector<Slice> pending;
    for (std::size_t i = 0; i < jobs.size(); ++i) {
        // @trace:ready
        pending.push_back({i, jobs[i].arrival, jobs[i].burst});
    }
    Time now = jobs.front().arrival;
    // This demo walks ALL original arrivals before the slices appended to the tail.
    // With large arrival gaps it can idle while earlier unfinished work is waiting.
    for (std::size_t cursor = 0; cursor < pending.size(); ++cursor) {
        // @trace:select
        const Slice slice = pending[cursor];
        Process& job = jobs[slice.process];
        if (now < slice.readyAt) {
            std::cout << "IDLE " << now << ' ' << slice.readyAt << '\n';
            // @trace:idle
            now = slice.readyAt;
        }
        const Time start = now;
        if (job.firstStart < 0) job.firstStart = start;
        const Time duration = std::min(quantum, slice.remaining);
        // @trace:run
        now += duration;
        std::cout << "RUN " << job.name << ' ' << start << ' ' << now << '\n';
        if (slice.remaining > duration) {
            // @trace:yield
            pending.push_back({slice.process, now, slice.remaining - duration});
        } else {
            // @trace:complete
            job.completion = now;
        }
    }
    // @trace:result
    printResults(jobs);
}

int main() {
    try {
        Time quantum = 2;
        std::vector<Process> jobs = readInput(quantum);
        simulate(jobs, quantum);
    } catch (const std::exception& error) {
        std::cerr << "Input/simulation error: " << error.what() << '\n';
        return 1;
    }
    return 0;
}
