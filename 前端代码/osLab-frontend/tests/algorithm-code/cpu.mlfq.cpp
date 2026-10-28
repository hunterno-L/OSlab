// Multilevel feedback queues, preserving the original queue and demotion behavior.
// Input: n queue_count, queue_count quanta, then n rows: name arrival burst.
// EOF uses quanta 1,2,4 and original A(1,13), B(0,8), C(1,6), D(1,5).
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

std::vector<Process> readInput(std::vector<Time>& quanta) {
    int count;
    std::cin >> std::ws;
    if (std::cin.eof()) {
        return {{"A", 1, 13}, {"B", 0, 8}, {"C", 1, 6}, {"D", 1, 5}};
    }
    if (!(std::cin >> count)) throw std::runtime_error("expected a process count");
    int levels;
    if (count < 1 || count > 1000 || !(std::cin >> levels) || levels < 2 || levels > 6)
        throw std::runtime_error("expected process count 1..1000 and queue count 2..6");
    quanta.resize(levels);
    for (Time& quantum : quanta)
        if (!(std::cin >> quantum) || quantum <= 0 || quantum > 1000000000LL)
            throw std::runtime_error("queue quanta must be 1..1e9");
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
    std::size_t level = 0;
    bool consumed = false;
};

void sortQueues(std::vector<Slice>& pending) {
    // Match the original exchange sort: level first, then queue arrival.
    for (std::size_t i = 0; i < pending.size(); ++i) {
        for (std::size_t j = i + 1; j < pending.size(); ++j) {
            if (pending[i].level > pending[j].level ||
                (pending[i].level == pending[j].level &&
                 pending[i].readyAt > pending[j].readyAt))
                std::swap(pending[i], pending[j]);
        }
    }
}

void simulate(std::vector<Process> jobs, const std::vector<Time>& quanta) {
    // @trace:initialize
    sortByArrival(jobs);
    std::vector<Slice> pending;
    for (std::size_t i = 0; i < jobs.size(); ++i) {
        // @trace:ready
        pending.push_back({i, jobs[i].arrival, jobs[i].burst, 0, false});
    }
    Time now = jobs.front().arrival;
    std::size_t completed = 0;
    while (completed < jobs.size()) {
        sortQueues(pending);
        std::size_t chosen = pending.size();
        Time nextArrival = std::numeric_limits<Time>::max();
        for (std::size_t i = 0; i < pending.size(); ++i) {
            if (pending[i].consumed) continue;
            nextArrival = std::min(nextArrival, pending[i].readyAt);
            if (chosen == pending.size() && pending[i].readyAt <= now) chosen = i;
        }
        // Handle arrival gaps safely instead of recursing with an undefined index.
        if (chosen == pending.size()) {
            std::cout << "IDLE " << now << ' ' << nextArrival << '\n';
            // @trace:idle
            now = nextArrival;
            continue;
        }
        // @trace:select
        const Slice slice = pending[chosen];
        pending[chosen].consumed = true;
        Process& job = jobs[slice.process];
        const Time start = now;
        if (job.firstStart < 0) job.firstStart = start;
        Time duration = std::min(slice.remaining, quanta[slice.level]);
        bool preempted = false;
        for (const Slice& candidate : pending) {
            if (!candidate.consumed && candidate.level < slice.level &&
                candidate.readyAt < start + duration) {
                // @trace:preempt
                duration = std::max<Time>(0, candidate.readyAt - start);
                preempted = true;
                break;
            }
        }
        // @trace:run
        now += duration;
        std::cout << "RUN " << job.name << ' ' << start << ' ' << now
                  << " Q" << slice.level + 1 << '\n';
        const Time remaining = slice.remaining - duration;
        if (remaining == 0) {
            // @trace:complete
            job.completion = now;
            ++completed;
        } else {
            // The demo also demotes a preempted job (despite its adjacent comment).
            // Clamp at the last queue to avoid the original out-of-range access.
            const std::size_t level = std::min(slice.level + 1, quanta.size() - 1);
            if (level > slice.level) {
                // @trace:demote
                pending.push_back({slice.process, now, remaining, level, false});
            } else {
                // @trace:yield
                pending.push_back({slice.process, now, remaining, level, false});
            }
            std::cout << (preempted ? "PREEMPT " : "YIELD ") << job.name
                      << " Q" << level + 1 << '\n';
        }
    }
    // @trace:result
    printResults(jobs);
}

int main() {
    try {
        std::vector<Time> quanta{1, 2, 4};
        std::vector<Process> jobs = readInput(quanta);
        simulate(jobs, quanta);
    } catch (const std::exception& error) {
        std::cerr << "Input/simulation error: " << error.what() << '\n';
        return 1;
    }
    return 0;
}
