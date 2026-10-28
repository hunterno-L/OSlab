// C-SCAN with the original requested-track wrap rule.
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
        return {53, {{0, 95}, {1, 183}, {2, 37}, {3, 122}, {4, 14}, {5, 130}, {6, 65}, {7, 80}}};
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
    std::stable_sort(data.requests.begin(), data.requests.end(),
        [](const Request& a, const Request& b) { return a.track < b.track; });
    // The original C-SCAN moves UP, wraps from highest REQUEST to lowest REQUEST,
    // then continues upward. No physical endpoints are inserted (C-LOOK).
    // The wrap distance is included in total movement and services its destination.
    const auto split = std::lower_bound(data.requests.begin(), data.requests.end(),
        data.initialHead, [](const Request& r, Track track) { return r.track < track; });
    const std::size_t middle = static_cast<std::size_t>(split - data.requests.begin());
    for (std::size_t i = middle; i < data.requests.size(); ++i) {
        // @trace:select
        Request& next = data.requests[i];
        seek(head, next);
    }
    for (std::size_t i = 0; i < middle; ++i) {
        // @trace:select
        Request& next = data.requests[i];
        if (i == 0) {
            // @trace:jump
            seek(head, next, "JUMP");
        } else {
            seek(head, next);
        }
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
