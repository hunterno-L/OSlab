// 首次适应（First Fit）分区分配
#include <iostream>
#include <string>
#include <vector>
using namespace std;

struct Partition {
    int id, size, rest;
};

struct Process {
    string name;
    int need;
};

void firstFit(vector<Partition> parts, vector<Process> jobs) {
    // @trace:initialize
    int failed = 0;
    for (const auto& job : jobs) {
        int pick = -1;
        for (int i = 0; i < (int)parts.size(); ++i) {
            // @trace:search
            // 从低地址开始，找第一个剩余空间足够的分区
            if (parts[i].rest >= job.need) { pick = i; break; }
        }
        if (pick < 0) {
            // @trace:fail
            // 没有分区装得下，分配失败
            ++failed;
            cout << job.name << " 分配失败" << '\n';
            continue;
        }
        // @trace:allocate
        // 从分区中划出所需空间
        parts[pick].rest -= job.need;
        cout << job.name << " -> 分区" << parts[pick].id
             << "，剩余 " << parts[pick].rest << '\n';
    }
    // @trace:result
    cout << "分配失败 " << failed << " 个进程" << '\n';
}

int main() {
    vector<Partition> parts = {
        {0, 90, 90}, {1, 50, 50},
        {2, 70, 70}, {3, 90, 90}};
    vector<Process> jobs = {
        {"作业A", 50}, {"作业B", 70},
        {"作业C", 50}, {"作业D", 100}};
    firstFit(parts, jobs);
}
