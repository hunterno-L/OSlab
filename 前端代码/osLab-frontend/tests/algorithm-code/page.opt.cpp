// Optimal page replacement. Input: frame_count reference_count, followed by page numbers.
// EOF uses 3 frames and references 4 3 2 1 4 3 5 4 3 2 1 5 from the original demo.
#include <algorithm>
#include <iomanip>
#include <iostream>
#include <stdexcept>
#include <vector>

struct Input {
    int frameCount = 3;
    std::vector<int> references{4, 3, 2, 1, 4, 3, 5, 4, 3, 2, 1, 5};
};
struct Frame {
    int page = -1;
    int age = 0;
    int nextUse = 0;
};

Input readInput() {
    Input data;
    std::cin >> std::ws;
    if (std::cin.eof()) {
        return data;
    }
    if (!(std::cin >> data.frameCount)) throw std::runtime_error("expected a frame count");
    int count;
    if (!(std::cin >> count) || data.frameCount < 1 || data.frameCount > 1000 ||
        count < 0 || count > 100000)
        throw std::runtime_error("expected 1..1000 frames and 0..100000 references");
    data.references.clear();
    for (int i = 0; i < count; ++i) {
        int page;
        if (!(std::cin >> page) || page < 0)
            throw std::runtime_error("page numbers must be nonnegative integers");
        data.references.push_back(page);
    }
    return data;
}

void printFrames(const std::vector<Frame>& frames) {
    std::cout << " FRAMES";
    for (const Frame& frame : frames) {
        if (frame.page < 0) std::cout << " -";
        else std::cout << ' ' << frame.page;
    }
    std::cout << '\n';
}

void printResults(const Input& data, int faults, int replacements) {
    const double rate = data.references.empty() ? 0.0 :
        100.0 * faults / data.references.size();
    std::cout << "faults " << faults << "\nreplacements " << replacements
              << "\nfault_rate " << std::fixed << std::setprecision(2) << rate << "%\n";
}

int searchFuture(const std::vector<int>& references, int now, int page) {
    for (int i = now + 1; i < static_cast<int>(references.size()); ++i) {
        // @trace:search
        if (references[i] == page) return i;
    }
    // A value above every valid index generalizes the demo's fixed sentinel 1000.
    return static_cast<int>(references.size());
}

void simulate(const Input& data) {
    // @trace:initialize
    std::vector<Frame> frames(data.frameCount);
    int faults = 0;
    int replacements = 0;
    for (int i = 0; i < static_cast<int>(data.references.size()); ++i) {
        const int page = data.references[i];
        int hit = -1;
        int empty = -1;
        for (int j = 0; j < data.frameCount; ++j) {
            // @trace:search
            if (frames[j].page == page) { hit = j; break; }
            if (empty < 0 && frames[j].page < 0) empty = j;
        }
        if (hit >= 0) {
            // @trace:hit
            frames[hit].nextUse = searchFuture(data.references, i, page);
            std::cout << "HIT " << i << ' ' << page << ' ' << hit;
            printFrames(frames);
            continue;
        }
        // @trace:fault
        ++faults;
        if (empty >= 0) {
            // @trace:load
            frames[empty].page = page;
            frames[empty].nextUse = searchFuture(data.references, i, page);
            std::cout << "LOAD " << i << ' ' << page << ' ' << empty;
        } else {
            int victim = 0;
            // Strict comparison retains the lowest frame index when values tie.
            for (int j = 1; j < data.frameCount; ++j) {
                // @trace:select
                if (frames[j].nextUse > frames[victim].nextUse) victim = j;
            }
            const int previous = frames[victim].page;
            // @trace:replace
            frames[victim].page = page;
            frames[victim].nextUse = searchFuture(data.references, i, page);
            ++replacements;
            std::cout << "REPLACE " << i << ' ' << page << ' ' << victim << ' ' << previous;
        }
        printFrames(frames);
    }
    // @trace:result
    printResults(data, faults, replacements);
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
