// Preemptive priority, larger number wins; equal priority does not preempt.
// Input: n, then n rows: name arrival burst priority. EOF uses original demo values.
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

std::vector<Process> readInput() {
    int count;
    std::cin >> std::ws;
    if (std::cin.eof()) {
        return {{"A", 0, 6, 1}, {"B", 2, 5, 5}, {"C", 8, 10, 3}, {"D", 10, 4, 2}};
    }
    if (!(std::cin >> count)) throw std::runtime_error("expected a process count");
    if (count < 1 || count > 1000) throw std::runtime_error("process count must be 1..1000");
    std::vector<Process> jobs;
    for (int i = 0; i < count; ++i) {
        Process job;
        if (!(std::cin >> job.name >> job.arrival >> job.burst >> job.priority))
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
    bool consumed = false;
};

void simulate(std::vector<Process> jobs) {
    // @trace:initialize
    sortByArrival(jobs, true);
    std::vector<Slice> pending;
    for (std::size_t i = 0; i < jobs.size(); ++i) {
        // @trace:ready
        pending.push_back({i, jobs[i].arrival, jobs[i].burst});
    }
    Time now = jobs.front().arrival;
    std::size_t current = 0;
    std::size_t completed = 0;
    while (completed < jobs.size()) {
        // @trace:select
        const Slice slice = pending[current];
        Process& job = jobs[slice.process];
        if (now < slice.readyAt) {
            std::cout << "IDLE " << now << ' ' << slice.readyAt << '\n';
            // @trace:idle
            now = slice.readyAt;
        }
        const Time start = now;
        Time end = start + slice.remaining;
        if (job.firstStart < 0) job.firstStart = start;
        pending[current].consumed = true;
        std::size_t next = current;
        // The original list is searched for the first future arrival that can preempt.
        for (std::size_t i = current; i < pending.size(); ++i) {
            if (pending[i].readyAt > start + slice.remaining) break;
            if (pending[i].consumed) continue;
            if (jobs[pending[i].process].priority > job.priority) {
                if (pending[i].readyAt < end) {
                    next = i;
                    end = std::max(start, pending[i].readyAt);
                }
                break;
            }
        }
        // @trace:run
        now = end;
        std::cout << "RUN " << job.name << ' ' << start << ' ' << now << '\n';
        const Time remaining = slice.remaining - (end - start);
        if (remaining > 0) {
            // Reinsert immediately behind the preempting arrival, as in the demo.
            const std::size_t insertion = next + 1;
            // @trace:preempt
            pending.insert(pending.begin() + insertion,
                           {slice.process, now, remaining, false});
            // Keep the demo's tail-only pairwise ordering after that insertion.
            for (std::size_t i = insertion + 1; i < pending.size(); ++i) {
                for (std::size_t j = i + 1; j < pending.size(); ++j) {
                    const int pi = jobs[pending[i].process].priority;
                    const int pj = jobs[pending[j].process].priority;
                    if (pending[i].readyAt > pending[j].readyAt ||
                        (pending[i].readyAt == pending[j].readyAt && pi < pj))
                        std::swap(pending[i], pending[j]);
                }
            }
            current = next;
        } else {
            // @trace:complete
            job.completion = now;
            ++completed;
            if (completed == jobs.size()) break;
            next = pending.size();
            for (std::size_t i = 0; i < pending.size(); ++i) {
                if (!pending[i].consumed) { next = i; break; }
            }
            for (std::size_t i = next; i < pending.size(); ++i) {
                if (pending[i].readyAt > now) break;
                if (!pending[i].consumed &&
                    jobs[pending[i].process].priority > jobs[pending[next].process].priority)
                    next = i;
            }
            current = next;
        }
    }
    // @trace:result
    printResults(jobs);
}

int main() {
    try {
        std::vector<Process> jobs = readInput();
        simulate(jobs);
    } catch (const std::exception& error) {
        std::cerr << "Input/simulation error: " << error.what() << '\n';
        return 1;
    }
    return 0;
}
