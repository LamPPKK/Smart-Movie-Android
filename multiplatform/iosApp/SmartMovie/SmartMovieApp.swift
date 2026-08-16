import SwiftUI

@main
struct SmartMovieApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                .ignoresSafeArea(.keyboard)
        }
    }
}
