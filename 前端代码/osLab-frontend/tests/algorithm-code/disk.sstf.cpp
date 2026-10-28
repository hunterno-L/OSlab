// Shortest seek time first.
// Input: request_count initial_head, followed by request tracks. EOF uses original demo data.
#include <algorithm>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <vector>

using Track = long long;
struct Request {
    int id;
    Track track;
    bool serviced = false;
};
struct Input {
    Track initialHead;
    std::vector<Request> requests;
};
struct Head {
    Track track;
    Track totalMovement = 0;
    int serviced = 0;
};

void seek(Head& head, Request& request, const char* action = "SEEK") {
    const Track previous = head.track;
    // @trace:seek
    const Track distance = std::llabs(request.track - head.track);
    head.totalMovement += distance;
    head.track = request.track;
    request.serviced = true;
    ++head.serviced;
    std::cout << action << ' ' << previous << ' ' << head.track << ' ' << distance
              << " REQUEST " << request.id << '\n';
}

void printResults(const Head& head) {
    const double average = head.serviced == 0 ? 0.0 :
        static_cast<double>(head.totalMovement) / head.serviced;
    std::cout << "total_movement " << head.totalMovement << "\naverage_movement "
              << std::fixed << std::setprecision(2) << average << '\n';
}

Input readInput() {
    int count;
    std::cin >> std::ws;
    if (std::cin.eof()) {
        return {80, {{0, 130}, {1, 100}, {2, 20}, {3, 60}, {4, 90}, {5, 140}}};
    }
    if (!(std::cin >> count)) throw std::runtime_error("expected a request count");
    Input data;
    if (count < 0 || count > 100000 || !(std::cin >> data.initialHead) ||
        data.initialHead < 0 || data.initialHead > 1000000000LL)
        throw std::runtime_error("expected 0..100000 requests and head track 0..1e9");
    for (int i = 0; i < count; ++i) {
        Track track;
        if (!(std::cin >> track) || track < 0 || track > 1000000000LL)
            throw std::runtime_error("request tracks must be 0..1e9");
        data.requests.push_back({i, track, false});
    }
    return data;
}

void simulate(Input data) {
    // @trace:initialize
    Head head{data.initialHead};
    // The demo sorts tracks BEFORE searching: equal distance chooses the lower track.
    std::stable_sort(data.requests.begin(), data.requests.end(),
        [](const Request& a, const Request& b) { return a.track < b.track; });
    while (head.serviced < static_cast<int>(data.requests.size())) {
        std::size_t chosen = data.requests.size();
        Track nearest = std::numeric_limits<Track>::max();
        for (std::size_t i = 0; i < data.requests.size(); ++i) {
            if (data.requests[i].serviced) continue;
            // @trace:search
            const Track distance = std::llabs(data.requests[i].track - head.track);
            if (distance < nearest) {
                // @trace:select
                chosen = i;
                nearest = distance;
            }
        }
        seek(head, data.requests[chosen]);
    }
    // @trace:result
    printResults(head);
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
