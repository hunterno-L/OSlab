// Next fit with reusable leftover space; no deallocation in this demo.
// Input: partition_count process_count, partition sizes, then rows: name required_size.
// EOF uses the values from the corresponding original teaching example. Partition IDs start at 0.
#include <algorithm>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

using Size = long long;
struct Partition {
    int id;
    Size size;
    Size remaining;
    std::vector<std::string> occupants;
};
struct Process {
    std::string name;
    Size need;
};
struct Input {
    std::vector<Partition> partitions;
    std::vector<Process> processes;
};

void printResults(const Input& data, int failures) {
    std::vector<Partition> byAddress = data.partitions;
    std::sort(byAddress.begin(), byAddress.end(),
              [](const Partition& a, const Partition& b) { return a.id < b.id; });
    std::cout << "\npartition size remaining processes\n";
    Size totalFree = 0;
    for (const Partition& partition : byAddress) {
        std::cout << partition.id << ' ' << partition.size << ' ' << partition.remaining;
        for (const std::string& name : partition.occupants) std::cout << ' ' << name;
        std::cout << '\n';
        totalFree += partition.remaining;
    }
    std::cout << "total_free " << totalFree << "\nfailed " << failures << '\n';
}

Input readInput() {
    int partitionCount;
    std::cin >> std::ws;
    if (std::cin.eof()) {
        return { {{0, 50, 50, {}}, {1, 20, 20, {}}, {2, 70, 70, {}}, {3, 50, 50, {}}, {4, 10, 10, {}}, {5, 90, 90, {}}, {6, 5, 5, {}}},
                 {{"work0", 70}, {"work1", 90}, {"work2", 5}, {"work3", 5}} };
    }
    if (!(std::cin >> partitionCount)) throw std::runtime_error("expected a partition count");
    int processCount;
    if (!(std::cin >> processCount) || partitionCount < 1 || partitionCount > 1000 ||
        processCount < 0 || processCount > 10000)
        throw std::runtime_error("expected 1..1000 partitions and 0..10000 processes");
    Input data;
    for (int i = 0; i < partitionCount; ++i) {
        Size size;
        if (!(std::cin >> size) || size <= 0 || size > 1000000000LL)
            throw std::runtime_error("partition sizes must be 1..1e9");
        data.partitions.push_back({i, size, size, {}});
    }
    for (int i = 0; i < processCount; ++i) {
        Process process;
        if (!(std::cin >> process.name >> process.need) ||
            process.need <= 0 || process.need > 1000000000LL)
            throw std::runtime_error("expected process name and size 1..1e9");
        data.processes.push_back(process);
    }
    return data;
}

void simulate(Input data) {
    // @trace:initialize
    int failures = 0;
    std::size_t cursor = 0;
    for (const Process& process : data.processes) {
        int chosen = -1;
        // Resume AT the last successful partition, so its leftover space is reusable.
        for (std::size_t checked = 0; checked < data.partitions.size(); ++checked) {
            const std::size_t i = (cursor + checked) % data.partitions.size();
            // @trace:search
            const bool fits = data.partitions[i].remaining >= process.need;
            std::cout << "CHECK " << process.name << ' ' << data.partitions[i].id
                      << ' ' << data.partitions[i].remaining << '\n';
            if (fits) {
                // @trace:select
                chosen = static_cast<int>(i);
                break;
            }
        }
        if (chosen < 0) {
            // @trace:fail
            ++failures;
            std::cout << "FAIL " << process.name << ' ' << process.need << '\n';
            continue;
        }
        Partition& partition = data.partitions[chosen];
        // A partition may hold several processes; each allocation consumes only its need.
        // @trace:allocate
        partition.remaining -= process.need;
        partition.occupants.push_back(process.name);
        cursor = static_cast<std::size_t>(chosen);
        std::cout << "ALLOCATE " << process.name << ' ' << partition.id << ' '
                  << process.need << ' ' << partition.remaining << '\n';
    }
    // @trace:result
    printResults(data, failures);
}

int main() {
    try {
        simulate(readInput());
    } catch (const std::exception& error) {
        std::cerr << "Input/simulation error: " << error.what() << '\n';
        return 1;
    }
    return 0;
}
