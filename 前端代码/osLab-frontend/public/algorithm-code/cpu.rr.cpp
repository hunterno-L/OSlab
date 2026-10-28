// 时间片轮转（RR）调度
#include <algorithm>
#include <iostream>
#include <queue>
#include <string>
#include <vector>
using namespace std;

struct PCB {
    string name;
    int arrive, work, remain;
    int start, finish;
};

bool byArrive(const PCB& a, const PCB& b) {
    return a.arrive < b.arrive;
}

void rr(vector<PCB> jobs, int quantum) {
    // @trace:initialize
    sort(jobs.begin(), jobs.end(), byArrive);
    for (auto& p : jobs) {
        p.remain = p.work;
        p.start = -1;
    }
    queue<int> ready;
    int now = jobs[0].arrive, next = 0, done = 0;
    while (done < (int)jobs.size()) {
        while (next < (int)jobs.size() && jobs[next].arrive <= now) {
            // @trace:ready
            // 新到达的进程进入就绪队列尾部
            ready.push(next++);
        }
        if (ready.empty()) {
            // @trace:idle
            // 就绪队列为空，CPU 等待下一个到达
            now = jobs[next].arrive;
            continue;
        }
        int i = ready.front();
        ready.pop();
        if (jobs[i].start < 0) jobs[i].start = now;
        int run = min(quantum, jobs[i].remain);
        // @trace:run
        // 进程最多运行一个时间片
        now += run;
        jobs[i].remain -= run;
        while (next < (int)jobs.size() && jobs[next].arrive <= now)
            ready.push(next++);
        if (jobs[i].remain > 0) {
            // @trace:yield
            // 时间片用完，排到队尾等待下一轮
            ready.push(i);
        } else {
            // @trace:complete
            jobs[i].finish = now;
            ++done;
            cout << jobs[i].name << " 完成于 " << now << '\n';
        }
    }
    // @trace:result
    for (const auto& p : jobs)
        cout << p.name << ": " << p.start << " -> " << p.finish << '\n';
}

int main() {
    vector<PCB> jobs = {
        {"A", 0, 10}, {"B", 0, 8},
        {"C", 1, 7}, {"D", 2, 13}};
    rr(jobs, 2);
}
