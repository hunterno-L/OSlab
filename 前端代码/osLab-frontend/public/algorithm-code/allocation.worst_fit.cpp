// 最坏适应（Worst Fit）分区分配
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

void worstFit(vector<Partition> parts, vector<Process> jobs) {
    // @trace:initialize
    int failed = 0;
    for (const auto& job : jobs) {
        int pick = -1;
        for (int i = 0; i < (int)parts.size(); ++i) {
            // @trace:search
            // 在所有够大的分区中选剩余空间最大的
            if (parts[i].rest >= job.need &&
                (pick < 0 || parts[i].rest > parts[pick].rest))
                pick = i;
        }
        if (pick < 0) {
            // @trace:fail
            // 没有分区装得下，分配失败
            ++failed;
            cout << job.name << " 分配失败" << '\n';
            continue;
        }
        // @trace:allocate
        // 从最大分区中划出所需空间
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
    worstFit(parts, jobs);
}
