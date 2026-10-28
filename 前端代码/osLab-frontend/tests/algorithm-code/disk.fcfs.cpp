// FCFS disk scheduling.
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
        return {100, {{0, 95}, {1, 183}, {2, 37}, {3, 122}, {4, 14}, {5, 130}, {6, 65}, {7, 80}}};
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
    for (Request& request : data.requests) {
        // @trace:select
        Request& next = request;
        seek(head, next);
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
