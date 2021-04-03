import open3d as o3d
import numpy as np
import cv2
import json
import math
import sys

cx_d = ''
cy_d = ''
fx_d = ''
fy_d = ''

def screenToWorld(depth, u, v):
    x_over_z = (cx_d - u) / fx_d
    y_over_z = (cy_d - v) / fy_d
    z = (depth / math.sqrt(1. + x_over_z**2 + y_over_z**2))
    x = x_over_z * z
    y = y_over_z * z
    return [x, y, z]

if len(sys.argv) != 2:
    print("syntax error: ", sys.argv[0], " <name_file_json>")
    sys.exit(1)

JSONFileName = sys.argv[1]

with open(JSONFileName) as json_file:
    data = json.load(json_file)
    scaleFactor = data['scale_factor']
    shiftFactor = data['shift_factor']
    fx_d = data['fx_d']
    fy_d = data['fy_d']
    cx_d = data['cx_d']
    cy_d = data['cy_d']

    depth = cv2.imread(data['fileDepth'], cv2.IMREAD_GRAYSCALE)
    rgb = cv2.imread(data['imageFileName'], cv2.IMREAD_COLOR)
    height, width = rgb.shape[:2]

    json_detections = data['detections']
    detections = json.loads(json_detections)

    depth = depth / 255
    depth = depth * scaleFactor + shiftFactor
    depth = 1 / depth

    xyz = np.empty(shape=(width*height, 3), dtype=np.float64)
    colors = np.empty(shape=(width*height, 3), dtype=np.float64)

    for u in range(0, width):
        for v in range(0, height):
            xyz[u+v*width] = screenToWorld(depth[v, u], u, v)
            colors[u+v*width] = [rgb[v, u, 2]/255, rgb[v, u, 1]/255, rgb[v, u, 0]/255]

    line_sets = []
    for i in range(len(detections)):
        person = detections[i]
        top = float(person['rectf_top'])
        bottom = float(person['rectf_bottom'])
        left = float(person['rectf_left'])
        right = float(person['rectf_right'])
        color = person['color']
        centerY = int((top + bottom)/2)
        centerX = int((left + right)/2)
        vertices = [screenToWorld(depth[centerY, centerX], left, top), 
                    screenToWorld(depth[centerY, centerX], right, top), 
                    screenToWorld(depth[centerY, centerX], right, bottom), 
                    screenToWorld(depth[centerY, centerX], left, bottom)]
        lines = [[0, 1], [1, 2], [2, 3], [3, 0]]
        lineColor = ''
        if color == 'RED':
            lineColor = [[1, 0, 0] for i in range(len(lines))]
        elif color == 'YELLOW':
            lineColor = [[1, 1, 0] for i in range(len(lines))]
        elif color == 'GREEN':
            lineColor = [[0, 1, 0] for i in range(len(lines))]
        line_set = o3d.geometry.LineSet(
            points=o3d.utility.Vector3dVector(vertices),
            lines=o3d.utility.Vector2iVector(lines),
        )
        line_set.colors = o3d.utility.Vector3dVector(lineColor)
        line_sets.append(line_set)

    pc = o3d.geometry.PointCloud()
    pc.points = o3d.utility.Vector3dVector(xyz)
    pc.colors = o3d.utility.Vector3dVector(colors)

    geometries = []
    geometries.append(pc)
    for i in range(len(line_sets)):
        geometries.append(line_sets[i])
        
    o3d.visualization.draw_geometries(geometries)
    